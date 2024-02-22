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
interface InfoOptionsUiStateMachine : StateMachine<Unit, ListGroupModel>
