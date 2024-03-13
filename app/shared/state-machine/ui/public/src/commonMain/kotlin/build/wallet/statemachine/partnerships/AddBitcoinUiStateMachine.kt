package build.wallet.statemachine.partnerships

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitkey.keybox.Keybox
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine
import com.github.michaelbull.result.Result

interface AddBitcoinUiStateMachine : StateMachine<AddBitcoinUiProps, SheetModel>

data class AddBitcoinUiProps(
  val purchaseAmount: FiatMoney?,
  val onAnotherWalletOrExchange: () -> Unit,
  val onPartnerRedirected: (PartnerRedirectionMethod) -> Unit,
  val onExit: () -> Unit,
  val keybox: Keybox,
  val generateAddress: suspend () -> Result<BitcoinAddress, Throwable>,
  val fiatCurrency: FiatCurrency,
  val onSelectCustomAmount: (minAmount: FiatMoney, maxAmount: FiatMoney) -> Unit,
)
