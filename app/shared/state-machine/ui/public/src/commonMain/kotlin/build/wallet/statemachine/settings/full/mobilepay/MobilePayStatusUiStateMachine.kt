package build.wallet.statemachine.settings.full.mobilepay

import build.wallet.limit.SpendingLimit
import build.wallet.money.currency.FiatCurrency
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData

/**
 * State machine for the UI for the mobile pay settings screen that shows
 * the current state of the spending limit
 */
interface MobilePayStatusUiStateMachine : StateMachine<MobilePayUiProps, BodyModel>

/**
 * @property fiatCurrency: The fiat currency to convert BTC amounts to and from.
 */
data class MobilePayUiProps(
  val onBack: () -> Unit,
  val accountData: ActiveFullAccountLoadedData,
  val fiatCurrency: FiatCurrency,
  val onSetLimitClick: (currentLimit: SpendingLimit?) -> Unit,
)
