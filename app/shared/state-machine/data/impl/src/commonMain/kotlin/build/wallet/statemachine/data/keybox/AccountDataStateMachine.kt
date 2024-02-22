package build.wallet.statemachine.data.keybox

import build.wallet.money.display.CurrencyPreferenceData
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigData.LoadedTemplateKeyboxConfigData

/**
 * Manages [Account] data state.
 */
interface AccountDataStateMachine : StateMachine<AccountDataProps, AccountData>

data class AccountDataProps(
  val templateKeyboxConfigData: LoadedTemplateKeyboxConfigData,
  val currencyPreferenceData: CurrencyPreferenceData,
)
