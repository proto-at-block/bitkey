package build.wallet.statemachine.recovery.conflict

import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * Drives the UI logic for the screens shown when [Recovery] is in a state of [NoLongerRecovering].
 *
 * This state means that the current app initiated a recovery that was then canceled elsewhere,
 * so we want to show the user an informative screen, and once they acknowledge it, we'll clear
 * the no longer relevant locally persisted recovery.
 */
interface NoLongerRecoveringUiStateMachine : StateMachine<NoLongerRecoveringUiProps, ScreenModel>

data class NoLongerRecoveringUiProps(
  val canceledRecoveryLostFactor: PhysicalFactor,
)
