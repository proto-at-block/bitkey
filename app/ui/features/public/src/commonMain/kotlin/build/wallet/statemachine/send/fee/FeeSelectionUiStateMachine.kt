package build.wallet.statemachine.send.fee

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.PsbtsForSendAmount
import build.wallet.money.exchange.ExchangeRate
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

interface FeeSelectionUiStateMachine : StateMachine<FeeSelectionUiProps, BodyModel>

/**
 * @property fiatCurrency: The fiat currency to convert BTC amounts to and from.
 * @property preBuiltPsbts: Pre-built PSBTs for different transaction priorities. When provided,
 *   the state machine will use these PSBTs instead of creating new ones.
 */
data class FeeSelectionUiProps(
  val recipientAddress: BitcoinAddress,
  val sendAmount: BitcoinTransactionSendAmount,
  val exchangeRates: ImmutableList<ExchangeRate>?,
  val preselectedPriority: EstimatedTransactionPriority? = null,
  val preBuiltPsbts: PsbtsForSendAmount? = null,
  val onBack: () -> Unit,
  val onContinue: (
    EstimatedTransactionPriority,
    ImmutableMap<EstimatedTransactionPriority, Fee>,
  ) -> Unit,
)
