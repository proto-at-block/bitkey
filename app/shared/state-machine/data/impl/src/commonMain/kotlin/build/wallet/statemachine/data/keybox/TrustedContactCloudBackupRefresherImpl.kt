package build.wallet.statemachine.data.keybox

import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.count.id.SocialRecoveryEventTrackerCounterId
import build.wallet.analytics.events.screen.EventTrackerCountInfo
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupRepository
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.FullAccountCloudBackupCreator
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.logging.LogLevel
import build.wallet.logging.logDebug
import build.wallet.logging.logFailure
import build.wallet.recovery.socrec.SocRecService
import build.wallet.statemachine.data.keybox.TrustedContactCloudBackupRefresherImpl.StoredBackupState.NeedsUpdate
import build.wallet.statemachine.data.keybox.TrustedContactCloudBackupRefresherImpl.StoredBackupState.UpToDate
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// TODO(BKR-1135): merge into FullAccountCloudBackupRepairer
class TrustedContactCloudBackupRefresherImpl(
  private val socRecService: SocRecService,
  private val cloudBackupDao: CloudBackupDao,
  private val cloudStoreAccountRepository: CloudStoreAccountRepository,
  private val cloudBackupRepository: CloudBackupRepository,
  private val fullAccountCloudBackupCreator: FullAccountCloudBackupCreator,
  private val eventTracker: EventTracker,
  private val clock: Clock,
) : TrustedContactCloudBackupRefresher {
  private val lastCheckState: MutableStateFlow<Instant> = MutableStateFlow(Instant.DISTANT_PAST)

  override val lastCheck: StateFlow<Instant> = lastCheckState

  override suspend fun refreshCloudBackupsWhenNecessary(
    scope: CoroutineScope,
    fullAccount: FullAccount,
  ) {
    scope.launch {
      combine(
        socRecService.socRecRelationships
          .filterNotNull()
          // Only endorsed and verified trusted contacts are interesting for cloud backups.
          .map {
            it.endorsedTrustedContacts
          }
          .distinctUntilChanged(),
        cloudBackupDao
          .backup(accountId = fullAccount.accountId.serverId)
          .distinctUntilChanged()
      ) { trustedContacts, cloudBackup ->
        coroutineBinding {
          val storedBackupState =
            cloudBackup.getStoredBackupState(trustedContacts)
              .bind()

          when (storedBackupState) {
            UpToDate -> return@coroutineBinding
            is NeedsUpdate -> {
              refreshCloudBackup(
                fullAccount = fullAccount,
                hwekEncryptedPkek = storedBackupState.hwekEncryptedPkek
              ).onSuccess {
                logDebug { "Refreshed cloud backup TC count=${trustedContacts.size}" }
              }.bind()
            }
          }
        }
      }.collect {
        it.logFailure(LogLevel.Warn) {
          "Failed to refresh cloud backup"
        }
        lastCheckState.value = clock.now()
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
            ?: return Err(Error("Lite Account Backups have no trusted contacts to refresh"))

        val backedUpRelationshipIds = fields.socRecSealedDekMap.keys
        val newRelationshipIds = endorsedTrustedContacts.map { it.relationshipId }.toSet()
        if (backedUpRelationshipIds == newRelationshipIds) {
          Ok(UpToDate)
        } else {
          val count: Int = endorsedTrustedContacts.count {
            it.authenticationState == TrustedContactAuthenticationState.VERIFIED
          }

          eventTracker.track(
            EventTrackerCountInfo(
              eventTrackerCounterId = SocialRecoveryEventTrackerCounterId.SOCREC_COUNT_TOTAL_TCS,
              count = count
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
