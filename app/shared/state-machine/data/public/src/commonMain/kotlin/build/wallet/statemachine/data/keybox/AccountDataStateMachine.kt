package build.wallet.statemachine.data.keybox

import build.wallet.statemachine.core.StateMachine

/**
 * Manages [Account] data state.
 */
interface AccountDataStateMachine : StateMachine<Unit, AccountData>
