package build.wallet.statemachine.data.keybox

import build.wallet.bitkey.account.LiteAccount
import build.wallet.money.display.CurrencyPreferenceData
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.account.OnboardConfigData
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigData

/**
 * Manages the state of the case when we have an active Lite Account ready to use.
 */
interface HasActiveLiteAccountDataStateMachine :
  StateMachine<HasActiveLiteAccountDataProps, AccountData>

data class HasActiveLiteAccountDataProps(
  val account: LiteAccount,
  val currencyPreferenceData: CurrencyPreferenceData,
  val accountUpgradeOnboardConfigData: OnboardConfigData.LoadedOnboardConfigData,
  val accountUpgradeTemplateKeyboxConfigData:
    TemplateKeyboxConfigData.LoadedTemplateKeyboxConfigData,
)
