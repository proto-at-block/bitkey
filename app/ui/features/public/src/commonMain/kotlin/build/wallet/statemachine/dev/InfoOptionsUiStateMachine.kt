package build.wallet.statemachine.dev

import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListGroupModel

/**
 * State machine for dev options to view various info, currently:
 * - account ID, if any
 * - app installation ID
 * - app version
 * - OS version
 */
interface InfoOptionsUiStateMachine : StateMachine<InfoOptionsProps, ListGroupModel>

data class InfoOptionsProps(
  val onPasteboardCopy: (name: String) -> Unit,
)
