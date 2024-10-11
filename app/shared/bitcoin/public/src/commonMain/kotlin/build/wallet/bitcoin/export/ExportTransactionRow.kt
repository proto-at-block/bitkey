package build.wallet.bitcoin.export

import build.wallet.bitcoin.export.ExportTransactionRow.ExportTransactionType
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.money.BitcoinMoney
import kotlinx.datetime.Instant

const val INCOMING_TRANSACTION_TYPE_STRING = "Incoming"
const val OUTGOING_TRANSACTION_TYPE_STRING = "Outgoing"
const val UTXO_CONSOLIDATION_TRANSACTION_TYPE_STRING = "UTXO Consolidation"
const val SWEEP_TRANSACTION_TYPE_STRING = "Recovery Sweep"

/**
 * A data structure representing a row in the customer's exported transaction history CSV.
 *
 * It supports confirmed transactions **ONLY**, and correctly handles send, receive, and
 * consolidation transactions.
 *
 * @property txid – The transaction's txid.
 * @property confirmationTime – Date and time (in GMT) of when the transaction was confirmed.
 * @property amount – Amount excluding fees, in sats.
 * @property fees – Fees paid for the transactions, in sats. It would be nil if it is an
 * inbound transaction.
 * @property transactionType – Type of transaction. We currently support showing "Incoming",
 * "Outgoing", and "Self Send"
 */
data class ExportTransactionRow(
  val txid: BitcoinTransactionId,
  val confirmationTime: Instant,
  val amount: BitcoinMoney,
  val fees: BitcoinMoney?,
  val transactionType: ExportTransactionType,
) {
  /**
   * A sealed interface representing the supported transaction types that we render in our transaction
   * export CSV.
   *
   * The cases here can really be thought of as a higher-fidelity version of the transaction type
   * used by [BitcoinTransaction].
   */
  sealed interface ExportTransactionType {
    /**
     * A row representing an inbound bitcoin transaction
     */
    data object Incoming : ExportTransactionType {
      override fun toString(): String = INCOMING_TRANSACTION_TYPE_STRING
    }

    /**
     * A row representing an outbound bitcoin transaction
     */
    data object Outgoing : ExportTransactionType {
      override fun toString(): String = OUTGOING_TRANSACTION_TYPE_STRING
    }

    /**
     * A row that represents a bitcoin transaction that was constructed as a consolidation of
     * pre-existing outputs that belonged to the customer's wallet.
     */
    data object UtxoConsolidation : ExportTransactionType {
      override fun toString(): String = UTXO_CONSOLIDATION_TRANSACTION_TYPE_STRING
    }

    /**
     * A row that represents two bitcoin transactions in a customer's wallet transaction history.
     * Specifically, the type would represent:
     * (1) An outgoing sweep transaction from an old customer wallet
     * (2) An incoming transaction to the customer's new wallet as a result of (1).
     *
     * Since these two transactions would have the same txid, amount, and fees paid, we use that as
     * a hint that they really should be considered a "Sweep".
     */
    data object Sweep : ExportTransactionType {
      override fun toString(): String = SWEEP_TRANSACTION_TYPE_STRING
    }
  }
}

fun BitcoinTransaction.TransactionType.toExportTransactionType(): ExportTransactionType {
  return when (this) {
    BitcoinTransaction.TransactionType.Incoming -> ExportTransactionType.Incoming
    BitcoinTransaction.TransactionType.Outgoing -> ExportTransactionType.Outgoing
    BitcoinTransaction.TransactionType.UtxoConsolidation -> ExportTransactionType.UtxoConsolidation
  }
}
