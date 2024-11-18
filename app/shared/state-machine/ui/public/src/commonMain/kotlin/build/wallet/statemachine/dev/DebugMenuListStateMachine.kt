package build.wallet.statemachine.dev

import build.wallet.fwup.FirmwareData
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for the main debug menu list UI. The parent state machine ([DebugMenuStateMachine])
 * handles showing the list or various other screens from actions from the list.
 */
interface DebugMenuListStateMachine : StateMachine<DebugMenuListProps, BodyModel>

/**
 * @property onSetState callback that sets the state on the parent state machine,
 *  [DebugMenuStateMachine].
 * @property onClose callback that is called when the debug menu is closed.
 *  context and the config has been modified via config options.
 */
data class DebugMenuListProps(
  val firmwareData: FirmwareData?,
  val onSetState: (DebugMenuState) -> Unit,
  val onClose: () -> Unit,
)
