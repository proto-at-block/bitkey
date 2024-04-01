package build.wallet.statemachine.data.recovery.conflict

import build.wallet.bitkey.factor.PhysicalFactor

/**
 * Represents states in the flow of screens shown to the user when
 * [Recovery] is in a state of [NoLongerRecovering].
 */
sealed interface NoLongerRecoveringData {
  /**
   * Indicates that we are showing the an informative screen to the user explaining that
   * a recovery they initiated is no longer in progress because it was canceled elsewhere.
   *
   * @property onAcknowledge: The user taps on the 'Got it' CTA, and we clear the now no
   * longer relevant locally persisted recovery.
   */
  data class ShowingNoLongerRecoveringData(
    val canceledRecoveryLostFactor: PhysicalFactor,
    val onAcknowledge: () -> Unit,
  ) : NoLongerRecoveringData

  /**
   * Indicates that we are in the process of clearing the locally persisted recovery.
   */
  data class ClearingLocalRecoveryData(
    val cancelingRecoveryLostFactor: PhysicalFactor,
  ) : NoLongerRecoveringData

  /**
   * Indicates that there was an issue when clearing the locally persisted recovery.
   *
   * @property rollback: Go back to [ShowingNoLongerRecoveringData]
   * @property retry: Retry the clearing.
   */
  data class ClearingLocalRecoveryFailedData(
    val error: Error,
    val cancelingRecoveryLostFactor: PhysicalFactor,
    val rollback: () -> Unit,
    val retry: () -> Unit,
  ) : NoLongerRecoveringData
}
