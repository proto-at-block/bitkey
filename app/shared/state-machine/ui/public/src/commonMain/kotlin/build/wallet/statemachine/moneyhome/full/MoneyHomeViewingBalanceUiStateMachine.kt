package build.wallet.statemachine.moneyhome.full

import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.recovery.socrec.SocRecFullAccountActions
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.firmware.FirmwareData
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.ui.model.status.StatusBannerModel

/**
 * State machine for the screen to show when the state of the Money Home screen is
 * [ViewingBalanceUiState].
 *
 * The overall Money Home experience (including showing screens for other states) is managed by
 * [MoneyHomeUiStateMachine], this is a child state machine specifically for when the balance is
 * showing.
 */
interface MoneyHomeViewingBalanceUiStateMachine : StateMachine<MoneyHomeViewingBalanceUiProps, ScreenModel>

data class MoneyHomeViewingBalanceUiProps(
  val accountData: AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData,
  val convertedFiatBalance: FiatMoney,
  val fiatCurrency: FiatCurrency,
  val firmwareData: FirmwareData,
  val socRecRelationships: SocRecRelationships,
  val socRecActions: SocRecFullAccountActions,
  val homeBottomSheetModel: SheetModel?,
  val homeStatusBannerModel: StatusBannerModel?,
  val onSettings: () -> Unit,
  val state: MoneyHomeUiState.ViewingBalanceUiState,
  val setState: (MoneyHomeUiState) -> Unit,
)
