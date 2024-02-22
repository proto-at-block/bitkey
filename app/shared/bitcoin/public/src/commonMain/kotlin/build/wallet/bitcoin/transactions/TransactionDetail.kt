package build.wallet.bitcoin.transactions

import kotlinx.datetime.Instant

/**
 * Bitcoin-specific transaction metadata.
 *
 * @property broadcastTime The time at which the transaction was broadcast.
 * @property transactionId The transaction ID.
 */
data class TransactionDetail(
  val broadcastTime: Instant,
  val transactionId: String,
)
