package build.wallet.statemachine.send

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.money.exchange.ExchangeRate
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

interface TransferConfirmationUiStateMachine :
  StateMachine<TransferConfirmationUiProps, ScreenModel>

/**
 * @property transferMoney the exact amount of money that the recipient will receive, does not
 * include mining fee.
 * @property requiredSigner factor that the transaction needs to be signed with.
 * @property exchangeRates The exchange rates at the time the customer launches the send flow.
 * @property onBack callback when we want to go back to the last state of the send flow
 * @property onExit callback when we want to exit the send flow
 */
data class TransferConfirmationUiProps(
  val variant: TransferConfirmationScreenVariant,
  val selectedPriority: EstimatedTransactionPriority,
  val recipientAddress: BitcoinAddress,
  val sendAmount: BitcoinTransactionSendAmount,
  val fees: ImmutableMap<EstimatedTransactionPriority, Fee>,
  val exchangeRates: ImmutableList<ExchangeRate>?,
  val onTransferInitiated: (psbt: Psbt, priority: EstimatedTransactionPriority) -> Unit,
  val onTransferFailed: () -> Unit,
  val onBack: () -> Unit,
  val onExit: () -> Unit,
)
