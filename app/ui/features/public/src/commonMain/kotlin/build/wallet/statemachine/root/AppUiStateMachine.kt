package build.wallet.statemachine.root

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * Top-level state machine for the app.
 */
interface AppUiStateMachine : StateMachine<Unit, ScreenModel>
