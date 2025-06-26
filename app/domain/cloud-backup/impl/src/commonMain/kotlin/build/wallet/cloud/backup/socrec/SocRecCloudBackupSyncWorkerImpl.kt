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
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.FullAccountCloudBackupCreator
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.backup.socrec.SocRecCloudBackupSyncWorkerImpl.StoredBackupState.NeedsUpdate
import build.wallet.cloud.backup.socrec.SocRecCloudBackupSyncWorkerImpl.StoredBackupState.UpToDate
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.LogLevel.Warn
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.relationships.RelationshipsService
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.*
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
  private val cloudBackupRepository: CloudBackupRepository,
  private val fullAccountCloudBackupCreator: FullAccountCloudBackupCreator,
  private val eventTracker: EventTracker,
  private val clock: Clock,
  private val appSessionManager: AppSessionManager,
) : SocRecCloudBackupSyncWorker {
  private val lastCheckState: MutableStateFlow<Instant> = MutableStateFlow(Instant.DISTANT_PAST)

  override val lastCheck: StateFlow<Instant> = lastCheckState

  override suspend fun executeWork() {
    val recovery = recoveryStatusService.status().map { it.get() }
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
      val activeRecovery = recovery as? StillRecovering
      val hasHardwareRecovery = activeRecovery?.factorToRecover == PhysicalFactor.Hardware
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
        // Only endorsed and verified Recovery Contacts are interesting for cloud backups.
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
  private fun CloudBackup?.getStoredBackupState(
    endorsedTrustedContacts: List<EndorsedTrustedContact>,
  ): Result<StoredBackupState, Error> {
    return when (this) {
      is CloudBackupV2 -> {
        val fields =
          fullAccountFields
            ?: return Err(Error("Lite Account Backups have no Recovery Contacts to refresh"))

        val backedUpRelationshipIds = fields.socRecSealedDekMap.keys
        val newRelationshipIds = endorsedTrustedContacts.map { it.relationshipId }.toSet()
        if (backedUpRelationshipIds == newRelationshipIds) {
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

  private suspend fun refreshCloudBackup(
    fullAccount: FullAccount,
    hwekEncryptedPkek: SealedCsek,
  ): Result<Unit, Error> =
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
      cloudBackupRepository.writeBackup(
        accountId = fullAccount.accountId,
        cloudStoreAccount = cloudStoreAccount,
        backup = cloudBackup,
        requireAuthRefresh = true
      ).bind()
    }
}
