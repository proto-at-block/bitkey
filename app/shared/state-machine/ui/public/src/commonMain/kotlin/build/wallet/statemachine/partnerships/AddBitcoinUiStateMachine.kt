package build.wallet.statemachine.partnerships

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitkey.keybox.Keybox
import build.wallet.money.FiatMoney
import build.wallet.partnerships.PartnerRedirectionMethod
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
  val onSelectCustomAmount: (minAmount: FiatMoney, maxAmount: FiatMoney) -> Unit,
)
