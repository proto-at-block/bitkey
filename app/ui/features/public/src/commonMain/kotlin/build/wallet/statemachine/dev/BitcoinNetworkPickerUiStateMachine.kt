package build.wallet.statemachine.dev

import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListGroupModel

/**
 * State machine for picking network type to use for the template keybox config.
 */
interface BitcoinNetworkPickerUiStateMachine : StateMachine<Unit, ListGroupModel?>
