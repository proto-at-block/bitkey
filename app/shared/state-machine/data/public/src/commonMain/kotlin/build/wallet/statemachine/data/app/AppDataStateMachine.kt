package build.wallet.statemachine.data.app

import build.wallet.statemachine.core.StateMachine

/**
 * Top-level data state machine for the app.
 */
interface AppDataStateMachine : StateMachine<Unit, AppData>
