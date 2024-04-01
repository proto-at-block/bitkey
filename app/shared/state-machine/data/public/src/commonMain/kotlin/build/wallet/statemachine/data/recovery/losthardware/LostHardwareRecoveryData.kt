package build.wallet.statemachine.data.recovery.losthardware

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData

/**
 * Describes Lost Hw DN recovery state.
 */
sealed interface LostHardwareRecoveryData {
  /**
   * Indicates that we are in process of initiating Lost Hw DN recovery.
   */
  sealed interface InitiatingLostHardwareRecoveryData : LostHardwareRecoveryData {
    /**
     * Generate new app keys since we rotate them for Lost Hardware recovery.
     */
    data object GeneratingNewAppKeysData : InitiatingLostHardwareRecoveryData

    data class ErrorGeneratingNewAppKeysData(
      val retry: () -> Unit,
      val cause: Throwable,
    ) : InitiatingLostHardwareRecoveryData

    /**
     * Indicates that we are awaiting new hardware keys.
     */
    data class AwaitingNewHardwareData(
      val newAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
      val addHardwareKeys: (SealedCsek, HwKeyBundle, AppGlobalAuthKeyHwSignature) -> Unit,
    ) : InitiatingLostHardwareRecoveryData

    /**
     * Indicates that we are initiating DN recovery with F8e.
     */
    data class InitiatingRecoveryWithF8eData(
      val rollback: () -> Unit,
    ) : InitiatingLostHardwareRecoveryData

    data class DisplayingConflictingRecoveryData(
      val onCancelRecovery: () -> Unit,
    ) : InitiatingLostHardwareRecoveryData

    data class FailedInitiatingRecoveryWithF8eData(
      val cause: Throwable,
      val retry: () -> Unit,
      val rollback: () -> Unit,
    ) : InitiatingLostHardwareRecoveryData

    data class VerifyingNotificationCommsData(
      val data: RecoveryNotificationVerificationData,
    ) : InitiatingLostHardwareRecoveryData

    data object CancellingConflictingRecoveryData : InitiatingLostHardwareRecoveryData

    data class FailedToCancelConflictingRecoveryData(
      val cause: Throwable,
      val onAcknowledge: () -> Unit,
    ) : InitiatingLostHardwareRecoveryData

    data class AwaitingHardwareProofOfPossessionKeyData(
      val onComplete: (HwFactorProofOfPossession) -> Unit,
      val rollback: () -> Unit,
    ) : InitiatingLostHardwareRecoveryData
  }

  /**
   * Indicates that hardware DN recovery has started and we are waiting for delay
   * or completing recovery.
   */
  data class LostHardwareRecoveryInProgressData(
    val recoveryInProgressData: RecoveryInProgressData,
  ) : LostHardwareRecoveryData
}
