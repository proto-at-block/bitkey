package build.wallet.statemachine.data.keybox.config

import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.statemachine.core.StateMachine

/**
 * Data state machine for reading and updating template [KeyboxConfig].
 */
interface TemplateKeyboxConfigDataStateMachine : StateMachine<Unit, TemplateKeyboxConfigData>
