package build.wallet.statemachine.partnerships

import build.wallet.bitkey.account.FullAccount
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine

interface AddBitcoinUiStateMachine : StateMachine<AddBitcoinUiProps, SheetModel>

data class AddBitcoinUiProps(
  val purchaseAmount: FiatMoney?,
  val onAnotherWalletOrExchange: () -> Unit,
  val onPartnerRedirected: (PartnerRedirectionMethod) -> Unit,
  val onExit: () -> Unit,
  val account: FullAccount,
  val fiatCurrency: FiatCurrency,
  val onSelectCustomAmount: (minAmount: FiatMoney, maxAmount: FiatMoney) -> Unit,
)
