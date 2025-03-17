package build.wallet.bitcoin.transactions

import kotlinx.datetime.Instant

/**
 * Bitcoin-specific transaction metadata for a broadcast transaction
 *
 * @property broadcastTime The time at which the transaction was broadcast.
 * @property transactionId The transaction ID.
 */
data class BroadcastDetail(
  val broadcastTime: Instant,
  val transactionId: String,
)
