package build.wallet.statemachine.send

import build.wallet.bitcoin.transactions.TransactionDetails
import build.wallet.money.exchange.ExchangeRate
import build.wallet.statemachine.core.StateMachine
import kotlinx.collections.immutable.ImmutableList

/**
 * State machine responsible for rendering money details of a Bitcoin transaction (transfer, fee,
 * total amounts, as well as transaction speed). Handles currency conversion.
 */
interface TransactionDetailsCardUiStateMachine :
  StateMachine<TransactionDetailsCardUiProps, TransactionDetailsModel>

data class TransactionDetailsCardUiProps(
  val transactionDetails: TransactionDetails,
  val exchangeRates: ImmutableList<ExchangeRate>?,
  val variant: TransferConfirmationScreenVariant,
)
