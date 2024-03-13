package build.wallet.statemachine.data.keybox.config

import build.wallet.statemachine.core.StateMachine

/**
 * Data state machine for reading and updating template [FullAccountConfig].
 */
interface TemplateFullAccountConfigDataStateMachine : StateMachine<Unit, TemplateFullAccountConfigData>
