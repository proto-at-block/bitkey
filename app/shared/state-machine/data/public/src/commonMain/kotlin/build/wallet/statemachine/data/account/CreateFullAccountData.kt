package build.wallet.statemachine.data.account

import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.keybox.KeyCrossDraft
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.nfc.transaction.PairingTransactionResponse

/**
 * States representing the process of creating a brand new Full Account.
 *
 * [CreateFullAccountData] is divided into three main states:
 * - CreateKeybox: Create keys and pair with HW and server
 * - OnboardKeybox: Back up data and set up notifications
 * - ActivateKeybox: Keybox transitions from onboarding -> active
 */
sealed interface CreateFullAccountData {
  /**
   * Describes the state of creating the initial keybox,
   * indicating that no onboarding is in progress.
   */
  sealed interface CreateKeyboxData : CreateFullAccountData {
    /**
     * Indicates that we are in process of generating app keys.
     */
    data class CreatingAppKeysData(
      val rollback: () -> Unit,
      val fullAccountConfig: FullAccountConfig,
    ) : CreateKeyboxData

    /**
     * App keys are generated, ready to pair hardware.
     *
     * [onPairHardwareComplete] progresses data to [HasAppAndHardwareKeysData].
     */
    data class HasAppKeysData(
      val appKeys: KeyCrossDraft.WithAppKeys,
      val rollback: () -> Unit,
      val fullAccountConfig: FullAccountConfig,
      val onPairHardwareComplete: (PairingTransactionResponse.FingerprintEnrolled) -> Unit,
    ) : CreateKeyboxData

    /**
     * App keys are generated, hardware is paired, ready to pair with server.
     *
     * [rollback] rolls the data state back to [HasAppKeysData].
     * [pairWithServer] initiate pairing with server. This will create new server account. Progresses
     * data to [PairingWithServerData].
     */
    data class HasAppAndHardwareKeysData(
      val rollback: () -> Unit,
    ) : CreateKeyboxData

    /**
     * App keys are generated, hardware is paired, pairing with server. After completion, progresses
     * data to [KeyboxCreatedData].
     */
    data object PairingWithServerData : CreateKeyboxData

    /**
     * Creating the keybox failed.
     */
    data class CreateKeyboxErrorData(
      val onBack: () -> Unit,
      val title: String = "We couldn’t create your wallet",
      val subline: String,
      val primaryButton: Button,
      val secondaryButton: Button? = null,
      val eventTrackerScreenId: CreateAccountEventTrackerScreenId,
    ) : CreateKeyboxData {
      data class Button(val text: String, val onClick: () -> Unit)
    }
  }

  sealed interface OnboardKeyboxDataFull : CreateFullAccountData {
    data object LoadingInitialStepDataFull : OnboardKeyboxDataFull

    /**
     * The keybox is being backed up to cloud storage.
     *
     * @property sealedCsek: The sealed CSEK (cloud storage encryption key) to use
     * to create the backup.
     */
    data class BackingUpKeyboxToCloudDataFull(
      val keybox: Keybox,
      val sealedCsek: SealedCsek?,
      val onBackupSaved: () -> Unit,
      val onBackupFailed: () -> Unit,
      val onExistingAppDataFound: (
        (
          cloudBackup: CloudBackup?,
          proceed: () -> Unit,
        ) -> Unit
      )? = null,
      val isSkipCloudBackupInstructions: Boolean,
    ) : OnboardKeyboxDataFull

    /** Cloud backup failed and must be retried. */
    data class FailedCloudBackupDataFull(
      val retry: () -> Unit,
    ) : OnboardKeyboxDataFull

    /**
     * The [CloudBackup] step is being marked as complete.
     */
    data object CompletingCloudBackupDataFull : OnboardKeyboxDataFull

    /**
     * The customer is providing (or opting out of) notification touchpoints.
     */
    data class SettingNotificationsPreferencesDataFull(
      val keybox: Keybox,
      val onComplete: () -> Unit,
    ) : OnboardKeyboxDataFull

    /**
     * The [NotificationPreferences] step is being marked as complete.
     */
    data object CompletingNotificationsDataFull : OnboardKeyboxDataFull
  }

  sealed interface ActivateKeyboxDataFull : CreateFullAccountData {
    /**
     * The keybox is being activated, transitioning from the onboarding keybox to the active one.
     */
    data object ActivatingKeyboxDataFull : ActivateKeyboxDataFull

    /**
     * There was an error when activating the keybox.
     */
    data class FailedToActivateKeyboxDataFull(
      val isConnectivityError: Boolean,
      val retry: () -> Unit,
      val onDeleteKeyboxAndExitOnboarding: () -> Unit,
    ) : ActivateKeyboxDataFull
  }

  /**
   * We found a full account cloud backup, but a new keybox is being onboarded. The user is given
   * the option to overwrite it or cancel onboarding the new keybox.
   */
  data class OverwriteFullAccountCloudBackupData(
    val keybox: Keybox,
    val onOverwrite: () -> Unit,
    val rollback: () -> Unit,
  ) : CreateFullAccountData

  /**
   * We found a lite account backup. The keybox is being offboarded, the full account deleted, and
   * then the lite account restored to be upgraded to a full account.
   */
  data class ReplaceWithLiteAccountRestoreData(
    val keyboxToReplace: Keybox,
    val liteAccountCloudBackup: CloudBackupV2,
    val onAccountUpgraded: (FullAccount) -> Unit,
    val onBack: () -> Unit,
  ) : CreateFullAccountData
}
