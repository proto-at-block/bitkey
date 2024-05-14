package build.wallet.statemachine.data.keybox

import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigData.LoadedTemplateFullAccountConfigData

/**
 * Manages [Account] data state.
 */
interface AccountDataStateMachine : StateMachine<AccountDataProps, AccountData>

data class AccountDataProps(
  val templateFullAccountConfigData: LoadedTemplateFullAccountConfigData,
)
