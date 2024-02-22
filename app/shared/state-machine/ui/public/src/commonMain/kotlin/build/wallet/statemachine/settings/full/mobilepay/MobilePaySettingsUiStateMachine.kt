package build.wallet.statemachine.settings.full.mobilepay

import build.wallet.money.currency.FiatCurrency
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData

/**
 * State machine for the entire screen (vs for just the UI in [MobilePayStatusUiStateMachine])
 * for the Mobile Pay settings screen. Handles deciding when to show
 * the mobile pay status UI (switch control showing current limit state) as well as when to
 * show the flow to set a new limit.
 */
interface MobilePaySettingsUiStateMachine : StateMachine<MobilePaySettingsUiProps, ScreenModel>

data class MobilePaySettingsUiProps(
  val onBack: () -> Unit,
  val accountData: ActiveFullAccountLoadedData,
  val fiatCurrency: FiatCurrency,
)
