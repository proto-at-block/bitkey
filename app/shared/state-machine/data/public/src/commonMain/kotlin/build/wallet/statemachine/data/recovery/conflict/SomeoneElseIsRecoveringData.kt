package build.wallet.statemachine.data.recovery.conflict

import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData

/**
 * Represents states in the flow of screens shown to the user when
 * [Recovery] is in a state of [SomeoneElseIsRecovering].
 */
sealed interface SomeoneElseIsRecoveringData {
  /**
   * Indicates that we are showing the an informative screen to the user explaining that
   * a recovery they didn't initiate (a recovery conflict) is currently in progress with
   * an action item for them to cancel it if they're the rightful owner.
   *
   * @property cancelingRecoveryLostFactor: the factor being recovered for the recovery on server
   * @property rollback: Closes this screen / flow
   * @property onCancelRecoveryConflict: The user taps on the 'Cancel' CTA, and we initiate
   * the cancellation process for the conflicting recovery.
   */
  data class ShowingSomeoneElseIsRecoveringData(
    val cancelingRecoveryLostFactor: PhysicalFactor,
    val onCancelRecoveryConflict: () -> Unit,
  ) : SomeoneElseIsRecoveringData

  /**
   * Indicates that we are in the process of canceling the conflicting recovery instance.
   *
   * @property cancelingRecoveryLostFactor: the factor being recovered for the recovery on server
   */
  data class CancelingSomeoneElsesRecoveryData(
    val cancelingRecoveryLostFactor: PhysicalFactor,
  ) : SomeoneElseIsRecoveringData

  /**
   * Indicates that there was a failure when trying to cancel the conflicting recovery instance.
   *
   * @property cancelingRecoveryLostFactor: the factor being recovered for the recovery on server
   * @property rollback: Go back to [ShowingSomeoneElseIsRecoveringData]
   * @property retry: Retry the cancellation.
   */
  data class CancelingSomeoneElsesRecoveryFailedData(
    val cancelingRecoveryLostFactor: PhysicalFactor,
    val rollback: () -> Unit,
    val retry: () -> Unit,
  ) : SomeoneElseIsRecoveringData

  data class AwaitingHardwareProofOfPossessionData(
    val onComplete: (HwFactorProofOfPossession) -> Unit,
    val rollback: () -> Unit,
  ) : SomeoneElseIsRecoveringData

  data class VerifyingNotificationCommsData(
    val data: RecoveryNotificationVerificationData,
  ) : SomeoneElseIsRecoveringData
}
