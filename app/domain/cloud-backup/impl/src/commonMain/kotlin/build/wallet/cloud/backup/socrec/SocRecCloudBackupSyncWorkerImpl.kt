package build.wallet.cloud.backup.socrec

import bitkey.recovery.RecoveryStatusService
import build.wallet.account.AccountService
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.count.id.InheritanceEventTrackerCounterId
import build.wallet.analytics.events.count.id.SocialRecoveryEventTrackerCounterId
import build.wallet.analytics.events.screen.EventTrackerCountInfo
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupOperationLock
import build.wallet.cloud.backup.CloudBackupService
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.FullAccountCloudBackupCreator
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.csek.SsekDao
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.backup.v2.FullAccountFields
import build.wallet.cloud.backup.socrec.SocRecCloudBackupSyncWorkerImpl.StoredBackupState.NeedsUpdate
import build.wallet.cloud.backup.socrec.SocRecCloudBackupSyncWorkerImpl.StoredBackupState.UpToDate
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.LogLevel.Warn
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.logging.logWarn
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.relationships.RelationshipsService
import build.wallet.wallet.migration.MigrationService
import build.wallet.wallet.migration.MigrationType
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// TODO(W-6693): merge into FullAccountCloudBackupRepairer
@BitkeyInject(AppScope::class)
class SocRecCloudBackupSyncWorkerImpl(
  private val accountService: AccountService,
  private val recoveryStatusService: RecoveryStatusService,
  private val relationshipsService: RelationshipsService,
  private val cloudBackupDao: CloudBackupDao,
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val cloudBackupService: CloudBackupService,
  private val fullAccountCloudBackupCreator: FullAccountCloudBackupCreator,
  private val eventTracker: EventTracker,
  private val clock: Clock,
  private val appSessionManager: AppSessionManager,
  private val cloudBackupOperationLock: CloudBackupOperationLock,
  private val ssekDao: SsekDao,
  private val migrationService: MigrationService,
) : SocRecCloudBackupSyncWorker {
  private val lastCheckState: MutableStateFlow<Instant> = MutableStateFlow(Instant.DISTANT_PAST)

  override val lastCheck: StateFlow<Instant> = lastCheckState

  override suspend fun executeWork() {
    val recovery = recoveryStatusService.status
    val account = accountService.activeAccount()

    combine(recovery, account) { activeRecovery, account ->
      /*
       * Only refresh cloud backups if we don't have an active hardware recovery in progress.
       * The CloudBackupRefresher updates the backup whenever a new backup is uploaded or when
       * SocRec relationships change.
       *
       * This prevents race conditions between cloud backup uploads performed
       *  - explicitly by Lost Hardware Recovery using a new but not yet active keybox.
       *  - implicitly by the CloudBackupRefresher using the active but about to be replaced keybox.
       * In the past we had a race (W-7790) that causes the app to upload a cloud backup for about to
       * be replaced keybox (but not yet currently active), resulting in a cloud backup with outdated
       * auth keys.
       *
       * TODO(W-8314): implement a more robust implementation for auto uploading cloud backups.
       */
      val stillRecovering = activeRecovery as? StillRecovering
      val hasHardwareRecovery = stillRecovering?.factorToRecover == PhysicalFactor.Hardware
      if (account is FullAccount && !hasHardwareRecovery) {
        account
      } else {
        null
      }
    }
      .distinctUntilChanged()
      .flatMapLatest { account ->
        account?.let {
          refreshCloudBackupsWhenNecessary(account)
        } ?: emptyFlow()
      }
      .collectLatest {
        it.logFailure(Warn) { "Failed to refresh cloud backup" }
        lastCheckState.value = clock.now()
      }
  }

  private fun refreshCloudBackupsWhenNecessary(account: FullAccount) =
    combine(
      relationshipsService.relationships
        .filterNotNull()
        // Only endorsed and verified trusted contacts are interesting for cloud backups.
        .map { it.endorsedTrustedContacts }
        .distinctUntilChanged(),
      cloudBackupDao
        .backup(accountId = account.accountId.serverId)
        .distinctUntilChanged(),
      appSessionManager.appSessionState.filter { AppSessionState.FOREGROUND == it }
    ) { trustedContacts, cloudBackup, _ ->
      coroutineBinding {
        val storedBackupState =
          cloudBackup.getStoredBackupState(trustedContacts)
            .bind()

        when (storedBackupState) {
          UpToDate -> return@coroutineBinding
          is NeedsUpdate -> {
            refreshCloudBackup(
              fullAccount = account,
              hwekEncryptedPkek = storedBackupState.hwekEncryptedPkek
            ).onSuccess {
              logInfo {
                "Cloud backup uploaded via SocRecCloudBackupSyncWorkerImpl; RC count=${trustedContacts.size}"
              }
            }.bind()
          }
        }
      }
    }

  /** Type for determining what action to take regarding the stored cloud backup. */
  private sealed interface StoredBackupState {
    /** No need to update */
    data object UpToDate : StoredBackupState

    /** Update using the attached [SealedCsek] */
    data class NeedsUpdate(
      val hwekEncryptedPkek: SealedCsek,
    ) : StoredBackupState
  }

  /** returns the [StoredBackupState] indicating whether the cloud backup needs to be refreshed. */
  private suspend fun CloudBackup?.getStoredBackupState(
    endorsedTrustedContacts: List<EndorsedTrustedContact>,
  ): Result<StoredBackupState, Error> {
    return when (this) {
      is CloudBackupV2, is CloudBackupV3 -> {
        val fields =
          fullAccountFields
            ?: return Err(Error("Lite Account Backups have no trusted contacts to refresh"))

        val backedUpRelationshipIds = fields.socRecSealedDekMap.keys
        val newRelationshipIds = endorsedTrustedContacts.map { it.id.value }.toSet()
        // Only consult SSEK staleness when the trusted-contact sets already match; a
        // relationship change forces a refresh regardless, so the extra store/DB reads
        // would be wasted work.
        val hasMissingSseks = backedUpRelationshipIds == newRelationshipIds && hasMissingSseks(fields)
        if (backedUpRelationshipIds == newRelationshipIds && !hasMissingSseks) {
          Ok(UpToDate)
        } else {
          val socRecCount = endorsedTrustedContacts.count {
            it.authenticationState == TrustedContactAuthenticationState.VERIFIED &&
              it.roles.contains(TrustedContactRole.SocialRecoveryContact)
          }

          eventTracker.track(
            EventTrackerCountInfo(
              eventTrackerCounterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
              count = socRecCount
            )
          )

          val inheritanceCount = endorsedTrustedContacts.count {
            it.authenticationState == TrustedContactAuthenticationState.VERIFIED &&
              it.roles.contains(TrustedContactRole.Beneficiary)
          }

          eventTracker.track(
            EventTrackerCountInfo(
              eventTrackerCounterId = InheritanceEventTrackerCounterId.INHERITANCE_COUNT_TOTAL_BENEFICIARIES,
              count = inheritanceCount
            )
          )

          Ok(
            NeedsUpdate(
              hwekEncryptedPkek = fields.sealedHwEncryptionKey
            )
          )
        }
      }

      null -> {
        // If the cloud backup is null we'll want to alert the customer, but for now
        // this is just treated like an error and logged.
        Err(Error("No cloud backup found"))
      }
    }
  }

  /**
   * Whether the local SSEK store holds keys not listed in the backup, requiring a refresh.
   *
   * Suppressed while a wallet migration is in progress: mid-migration the local SSEK store
   * already contains keys sealed by the new hardware, so this check would otherwise upload
   * a backup built from transitional keybox state. The migration flow uploads its own backup,
   * and any remaining SSEK staleness is picked up on the next sync pass after completion.
   *
   * An SSEK read failure counts as stale so an incomplete backup is repaired on a later pass
   * rather than being reported as up to date.
   */
  private suspend fun hasMissingSseks(fields: FullAccountFields): Boolean {
    if (isMigrationInProgress()) return false
    return ssekDao.getAllSealedIds()
      .logFailure(Warn) { "Failed to read SSEK ids for cloud backup staleness check" }
      .map { localSsekIds ->
        (localSsekIds.map { it.hex() }.toSet() - fields.sealedSsekIds).isNotEmpty()
      }
      // Fail toward refresh: a read failure must not mask a backup that is missing SSEKs.
      .getOr(true)
  }

  /**
   * Whether any wallet migration (private wallet migration or W3 upgrade) is in progress.
   *
   * Checks durable persisted migration state in addition to [MigrationService.resume]:
   * resume maps some persisted-but-early checkpoints back to NotStarted for flow routing,
   * which would otherwise open this guard during the exact window it protects (e.g. app
   * restart after a new SSEK is stored but before descriptor backup completes).
   *
   * Fails closed: when migration state cannot be read, it is treated as in progress so the
   * SSEK staleness check stays suppressed rather than risking a refresh from transitional
   * keybox state. The next sync pass retries.
   */
  private suspend fun isMigrationInProgress(): Boolean {
    return MigrationType.entries.any { type ->
      val inProgress = migrationService.resume(type)
        .onFailure {
          logWarn { "Failed to check $type migration state for cloud backup sync: $it" }
        }
        .map { it.isInProgress() }
        .getOr(true)
      inProgress || migrationService.hasPersistedMigrationState(type)
        .onFailure {
          logWarn { "Failed to check $type persisted migration state for cloud backup sync: $it" }
        }
        .getOr(true)
    }
  }

  private suspend fun refreshCloudBackup(
    fullAccount: FullAccount,
    hwekEncryptedPkek: SealedCsek,
  ): Result<Unit, Error> =
    // CRITICAL: Do NOT call CloudBackupHealthRepository.performSync() or
    // CloudBackupVersionMigrationWorker.executeWork() within this block - it would cause a deadlock.
    cloudBackupOperationLock.withLock {
      coroutineBinding {
        // Get the customer's cloud store account.
        val cloudStoreAccount =
          cloudStoreAccountRepository
            .currentAccount(cloudServiceProvider())
            .toErrorIfNull {
              // If the account is null we'll want to alert the customer, but for now
              // this is just treated like an error and logged.
              Error("Cloud store account not found")
            }
            .bind()

        // Create a new cloud backup.
        val cloudBackup = fullAccountCloudBackupCreator
          .create(keybox = fullAccount.keybox, sealedCsek = hwekEncryptedPkek)
          .bind()

        // Upload new cloud backup to the cloud.
        cloudBackupService.writeBackup(
          accountId = fullAccount.accountId,
          cloudStoreAccount = cloudStoreAccount,
          backup = cloudBackup,
          requireAuthRefresh = true
        ).bind()
      }
    }
}
