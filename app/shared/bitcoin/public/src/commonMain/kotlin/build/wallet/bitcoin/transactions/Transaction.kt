package build.wallet.bitcoin.transactions

import build.wallet.money.exchange.ExchangeRate
import kotlinx.datetime.Instant

/**
 * Represents information about a transaction that a customer makes.
 *
 * @property transactionDetail The details of the transaction.
 * @property exchangeRates The exchange rates at the time of broadcast, null when there were no
 * exchange rates available at the time of broadcast.
 * @property estimatedConfirmationTime Estimated time the transaction should confirm based on the customer's selected fee rate.
 */
data class Transaction(
  val transactionDetail: TransactionDetail,
  val exchangeRates: List<ExchangeRate>?,
  val estimatedConfirmationTime: Instant,
)
