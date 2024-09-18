package build.wallet.statemachine.send.fee

import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.money.BitcoinMoney
import build.wallet.money.exchange.ExchangeRate
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.core.form.FormMainContentModel.FeeOptionList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

interface FeeOptionListUiStateMachine : StateMachine<FeeOptionListProps, FeeOptionList>

/**
 * Properties for the fee option list.
 *
 * @property transactionBaseAmount amount customer is trying to send, before fees
 * @property fees map of priority to amount in fees
 * @property defaultPriority pre-selected priority in the fee option screen
 * @property exchangeRates exchange rates locked in from the start of the send flow
 * @property onOptionSelected callback when customer affirms their fee option selection
 */
data class FeeOptionListProps(
  val transactionBaseAmount: BitcoinMoney,
  val fees: ImmutableMap<EstimatedTransactionPriority, Fee>,
  val defaultPriority: EstimatedTransactionPriority,
  val exchangeRates: ImmutableList<ExchangeRate>?,
  val onOptionSelected: (EstimatedTransactionPriority) -> Unit,
)
