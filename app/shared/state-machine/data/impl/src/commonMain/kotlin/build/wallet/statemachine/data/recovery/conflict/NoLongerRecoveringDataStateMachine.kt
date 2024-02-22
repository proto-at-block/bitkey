package build.wallet.statemachine.data.recovery.conflict

import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.statemachine.core.StateMachine

/**
 * Drives the logic for the screens shown when [Recovery] is in a state of [NoLongerRecovering].
 *
 * This state means that the current app initiated a recovery that was then canceled elsewhere,
 * so we  want to show the user an informative screen, and once they acknowledge it, we'll clear
 * the no longer relevant locally persisted recovery.
 */
interface NoLongerRecoveringDataStateMachine :
  StateMachine<NoLongerRecoveringDataStateMachineDataProps, NoLongerRecoveringData>

/**
 * @property cancelingRecoveryLostFactor: The lost factor for the server recovery that canceled our local recovery.
 */
data class NoLongerRecoveringDataStateMachineDataProps(
  val cancelingRecoveryLostFactor: PhysicalFactor,
)
