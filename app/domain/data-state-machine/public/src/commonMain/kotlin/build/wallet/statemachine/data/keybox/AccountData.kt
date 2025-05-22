package build.wallet.statemachine.data.keybox

import build.wallet.auth.PendingAuthKeyRotationAttempt
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.cloud.backup.CloudBackup
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData

/**
 * Describes Account state in the app. This could be Customer or Recovery Contact
 * account.
 */
sealed interface AccountData {
  /**
   * Checking to see if we have an active account.
   */
  data object CheckingActiveAccountData : AccountData

  /**
   * Indicates that there is no active account.
   */
  sealed interface NoActiveAccountData : AccountData {
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
      val startEmergencyAccessRecovery: () -> Unit,
      val wipeExistingDevice: () -> Unit,
    ) : NoActiveAccountData

    /**
     * Indicates that there is an ongoing "Lost App" recovery in progress for a [FullAccount].
     */
    data class RecoveringAccountData(
      val lostAppRecoveryData: LostAppRecoveryData,
    ) : NoActiveAccountData

    /**
     * This is a non-onboarded state with a template config used for the non-onboarded device reset flow
     */
    data class ResettingExistingDeviceData(
      val onExit: () -> Unit,
      val onSuccess: () -> Unit,
    ) : NoActiveAccountData

    /**
     * Indicates that the account is attempting recovery with the Emergency Exit Kit.
     */
    data class RecoveringAccountWithEmergencyAccessKit(
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
      val onStartCloudRecovery: (CloudBackup) -> Unit,
      val onStartLostAppRecovery: () -> Unit,
      val onImportEmergencyAccessKit: () -> Unit,
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
     * become a Recovery Contact.
     */
    BeTrustedContact,

    /**
     * Indicates onboarding was started with the intent to become a beneficiary.
     */
    BeBeneficiary,
  }

  /**
   * An active Full Account is present.
   */
  sealed interface HasActiveFullAccountData : AccountData {
    /**
     *
     * A keybox associated with the Full Account.
     */
    val account: FullAccount

    data class RotatingAuthKeys(
      override val account: FullAccount,
      val pendingAttempt: PendingAuthKeyRotationAttempt,
    ) : HasActiveFullAccountData

    /**
     * An active Full Account is present.
     *
     * @property lostHardwareRecoveryData provides Hardware Recovery data for the active account.
     */
    data class ActiveFullAccountLoadedData(
      override val account: FullAccount,
      val lostHardwareRecoveryData: LostHardwareRecoveryData,
    ) : HasActiveFullAccountData
  }

  /**
   * The keybox was recovering but it was canceled elsewhere, notify customer
   */
  data class NoLongerRecoveringFullAccountData(
    val canceledRecoveryLostFactor: PhysicalFactor,
  ) : AccountData

  /**
   * The current Full Account has a recovery happening elsewhere, notifying customer.
   */
  data class SomeoneElseIsRecoveringFullAccountData(
    val data: SomeoneElseIsRecoveringData,
    val fullAccountId: FullAccountId,
  ) : AccountData
}
