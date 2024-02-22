package build.wallet.statemachine.home.full

import build.wallet.money.display.CurrencyPreferenceData
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.firmware.FirmwareData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData
import build.wallet.statemachine.data.sync.ElectrumServerData

/**
 * State machine for managing the home (money home + settings) experiences for a "full" customer.
 */
interface HomeUiStateMachine : StateMachine<HomeUiProps, ScreenModel>

data class HomeUiProps(
  val accountData: ActiveFullAccountLoadedData,
  val electrumServerData: ElectrumServerData,
  val firmwareData: FirmwareData,
  val currencyPreferenceData: CurrencyPreferenceData,
)
