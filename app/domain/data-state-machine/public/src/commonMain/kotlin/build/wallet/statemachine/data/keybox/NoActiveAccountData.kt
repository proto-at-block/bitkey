package build.wallet.statemachine.data.keybox

import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData

/**
 * Describes state when there is no active account in the app.
 */
sealed interface NoActiveAccountData {
  /**
   * We are checking if there is any persisted recovery or onboarding at app launch.
   */
  data object CheckingRecovery : NoActiveAccountData

  /**
   * Indicates that there is no active account and no onboarding or recovery in progress.
   */
  data class GettingStartedData(
    val startLiteAccountCreation: () -> Unit,
    val startRecovery: () -> Unit,
    val startEmergencyExitRecovery: () -> Unit,
  ) : NoActiveAccountData

  /**
   * Indicates that there is an ongoing "Lost App" recovery in progress for a [FullAccount].
   */
  data class RecoveringAccountData(
    val lostAppRecoveryData: LostAppRecoveryData,
  ) : NoActiveAccountData

  /**
   * Indicates that the account is attempting recovery with the Emergency Exit Kit.
   */
  data class RecoveringAccountWithEmergencyExitKit(
    val onExit: () -> Unit,
  ) : NoActiveAccountData

  /**
   * Indicates that the account is recovering from orphaned keychain keys after app deletion.
   */
  data class RecoveringFromOrphanedKeysData(
    val uiState: OrphanedKeyRecoveryUiState,
    val onRecover: () -> Unit,
    val onRetry: () -> Unit,
    val onExit: () -> Unit,
  ) : NoActiveAccountData

  /**
   * Indicates that the application is loading cloud backup data to determine how to proceed.
   *
   * @property intent Indicator for the user's intended action when starting onboarding or recovery. Note that this
   *   may not determine the final destination of the experience, depending on the type of cloud backup data found.
   * @property onStartCloudRecovery Invoked when the application should start recovery for an existing full account
   * from the backup provided.
   * @property onStartLostAppRecovery Invoked when the application should start recovery for an existing account
   * using hardware recovery.
   * @property onExit Invoked to return back to the previous screen.
   */
  data class CheckingCloudBackupData(
    val intent: StartIntent,
    val inviteCode: String? = null,
    val onStartCloudRecovery: (CloudStoreAccount, CloudBackup) -> Unit,
    val onStartLostAppRecovery: () -> Unit,
    val onImportEmergencyExitKit: () -> Unit,
    val onExit: () -> Unit,
  ) : NoActiveAccountData
}

/**
 * Navigation intent used to inform the destination of the getting
 * started experience.
 */
enum class StartIntent {
  /**
   * Indicates that the user started onboarding with the intent to
   * restore an existing Bitkey account.
   */
  RestoreBitkey,

  /**
   * Indicates that the user started onboarding with the intent to
   * become a trusted contact.
   */
  BeTrustedContact,

  /**
   * Indicates onboarding was started with the intent to become a beneficiary.
   */
  BeBeneficiary,
}

enum class OrphanedKeyRecoveryUiState {
  ShowingPrompt,
  Recovering,
  RestoringAccount,
  Success,
  Error,
}
