package build.wallet.bitcoin.utxo

import build.wallet.bitcoin.transactions.BroadcastDetail
import kotlinx.datetime.Instant

/**
 * Represents the details of a broadcasted UTXO consolidation transaction.
 *
 * @param broadcastDetail bitcoin specific broadcast details of the transaction.
 * @param estimatedConfirmationTime estimated time when the consolidation will be confirmed.
 */
data class UtxoConsolidationTransactionDetail(
  val broadcastDetail: BroadcastDetail,
  val estimatedConfirmationTime: Instant,
)
