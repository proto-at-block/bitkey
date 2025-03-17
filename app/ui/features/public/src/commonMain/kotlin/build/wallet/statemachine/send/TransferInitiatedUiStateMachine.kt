package build.wallet.statemachine.send

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.money.exchange.ExchangeRate
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.transactions.TransactionDetails
import kotlinx.collections.immutable.ImmutableList

/**
 * State machine for showing transfer success state.
 */
interface TransferInitiatedUiStateMachine : StateMachine<TransferInitiatedUiProps, BodyModel>

/**
 * @property onBack - handler for when the state machine exits.
 * @property onDone - handler for when the state machine is closed successfully.
 */
data class TransferInitiatedUiProps(
  val onBack: () -> Unit,
  val recipientAddress: BitcoinAddress,
  val transactionDetails: TransactionDetails,
  val exchangeRates: ImmutableList<ExchangeRate>?,
  val onDone: () -> Unit,
)
