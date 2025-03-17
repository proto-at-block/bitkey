package build.wallet.statemachine.send.amountentry

import androidx.compose.runtime.Immutable
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.send.TransferAmountUiState

interface TransferCardUiStateMachine : StateMachine<TransferCardUiProps, CardModel?>

@Immutable
data class TransferCardUiProps(
  val bitcoinBalance: BitcoinBalance,
  val enteredBitcoinMoney: BitcoinMoney,
  val transferAmountState: TransferAmountUiState,
  val onSendMaxClick: (() -> Unit),
  val onHardwareRequiredClick: (() -> Unit),
)
