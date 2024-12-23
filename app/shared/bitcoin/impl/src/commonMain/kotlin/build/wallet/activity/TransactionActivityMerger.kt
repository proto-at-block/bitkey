package build.wallet.activity

import build.wallet.activity.Transaction.BitcoinWalletTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Incoming
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Outgoing
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.partnerships.PartnershipTransactionType
import build.wallet.partnerships.PartnershipTransactionType.*
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.datetime.Instant.Companion.DISTANT_FUTURE

/**
 * A simple wrapper around [mergeTransactions] and [sortTransactions].
 */
fun mergeAndSortTransactions(
  partnershipTransactions: List<PartnershipTransaction>,
  bitcoinTransactions: List<BitcoinTransaction>,
): List<Transaction> =
  sortTransactions(mergeTransactions(partnershipTransactions, bitcoinTransactions))

/**
 * Merges a list of partnership and bitcoin transactions using the following rules:
 * 1. If partnershipTransaction.txid == bitcoinTransaction.id, the transactions are merged
 * 2. If the crypto amounts and type (i.e. send/receive) are the same, and only ONE match is found,
 *    the transactions are merged.
 */
fun mergeTransactions(
  partnershipTransactions: List<PartnershipTransaction>,
  bitcoinTransactions: List<BitcoinTransaction>,
): List<Transaction> {
  val mergedTransactions = mutableListOf<Transaction>()

  // Create a map of Partnership transactions by txid for quick lookup
  val partnershipTxByTxId = partnershipTransactions
    .filter { it.txid != null }
    .associateBy { it.txid }

  // Track unmatched transactions for efficient lookup and future iteration to add the stragglers.
  val unmatchedBitcoinTransactionIds = bitcoinTransactions.map { it.id }.toMutableSet()
  val unmatchedPartnershipTransactionIds = partnershipTransactions.map { it.id }.toMutableSet()

  // Step 1: Merge transactions based on txid match
  for (bitcoinTransaction in bitcoinTransactions) {
    val partnershipTransaction = partnershipTxByTxId[bitcoinTransaction.id]

    if (partnershipTransaction != null) {
      // Matching txid found, add merged transaction
      mergedTransactions.add(
        Transaction.PartnershipTransaction(
          details = partnershipTransaction,
          bitcoinTransaction = bitcoinTransaction
        )
      )

      unmatchedPartnershipTransactionIds.remove(partnershipTransaction.id)
      unmatchedBitcoinTransactionIds.remove(bitcoinTransaction.id)
    }
  }

  // Step 2: Additional matching based on amount and type
  for (partnershipTransaction in partnershipTransactions) {
    if (!unmatchedPartnershipTransactionIds.contains(partnershipTransaction.id)) {
      continue
    }

    val potentialMatches = bitcoinTransactions.filter { bitcoinTransaction ->
      val isUnmatched = unmatchedBitcoinTransactionIds.contains(bitcoinTransaction.id)
      val amountMatched =
        partnershipTransaction.cryptoAmount?.toBigDecimal() == bitcoinTransaction.subtotal.value
      val transactionTypeIsEqual =
        transactionTypeIsEqual(partnershipTransaction.type, bitcoinTransaction.transactionType)

      isUnmatched && amountMatched && transactionTypeIsEqual
    }

    if (potentialMatches.size == 1) {
      // Unique match found, add merged transaction and mark Bitcoin transaction as matched
      val bitcoinTransaction = potentialMatches[0]
      mergedTransactions.add(
        Transaction.PartnershipTransaction(
          details = partnershipTransaction,
          bitcoinTransaction = bitcoinTransaction
        )
      )
      unmatchedPartnershipTransactionIds.remove(partnershipTransaction.id)
      unmatchedBitcoinTransactionIds.remove(potentialMatches.first().id)
    } else {
      // No unique match, add PartnershipTransaction as standalone entry
      mergedTransactions.add(
        Transaction.PartnershipTransaction(
          details = partnershipTransaction,
          bitcoinTransaction = null
        )
      )
    }
  }

  // Step 3: Add any unmatched Bitcoin transactions as standalone entries
  for (bitcoinTransaction in bitcoinTransactions) {
    if (unmatchedBitcoinTransactionIds.contains(bitcoinTransaction.id)) {
      mergedTransactions.add(BitcoinWalletTransaction(details = bitcoinTransaction))
    }
  }

  return mergedTransactions
}

/**
 * Sorts a list of [Transaction]s according to the following rules:
 * 1. First, transactions are bucketed by status (pending -> completed & failed)
 * 2. Then, within each of these buckets, we group by whether we have a confirmation time or not.
 *    - Fallback to the appropriate sorting bucket depending on transaction type
 *        - for Canceled transactions, sort by created_at
 *        - for Pending transactions, sort partnerships by created_at, onChain by id
 *        - for Completed transactions, sort by confirmation time
 * 3. Finally, sort by selected field now that we're guaranteed to compare apples to apples. We sort by descending order,
 *    so more recent transactions appear first in a given bucket.
 */
fun sortTransactions(transactions: List<Transaction>): List<Transaction> {
  val transactionComparator = Comparator<Transaction> { t1, t2 ->
    compareValuesBy(
      t1, t2,
      // Primary sorting by status order: Failed (0), Pending (1), Completed (2)
      { transaction ->
        when (transaction.status()) {
          TransactionStatus.PENDING -> 0
          TransactionStatus.CONFIRMED, TransactionStatus.FAILED -> 1
        }
      },
      { transaction ->
        when (transaction) {
          // Separate transactions of the same type that have a confirmation time and those that
          // don't - this way we can ensure we're comparing apples to apples
          is Transaction.PartnershipTransaction -> if (transaction.bitcoinTransaction?.confirmationTime() == null) 0 else 2
          is BitcoinWalletTransaction -> if (transaction.details.confirmationTime() == null) 1 else 2
        }
      },
      // Tertiary sorting within each type-specific category
      // Since we want the newest timestamps to appear first in the list, we effectively negate the timestamp by subtracting
      // it from DISTANT_FUTURE.
      { transaction ->
        when (transaction) {
          is BitcoinWalletTransaction ->
            transaction.details.confirmationTime()?.let { DISTANT_FUTURE.minus(it) }
              ?: transaction.details.id

          is Transaction.PartnershipTransaction -> {
            DISTANT_FUTURE.minus(
              transaction.bitcoinTransaction?.confirmationTime() ?: transaction.details.created
            )
          }
        }
      }
    )
  }
  return transactions.sortedWith(comparator = transactionComparator)
}

private fun transactionTypeIsEqual(
  partnershipType: PartnershipTransactionType,
  bitcoinTransactionType: BitcoinTransaction.TransactionType,
): Boolean {
  return (partnershipType == PURCHASE && bitcoinTransactionType == Incoming) ||
    (partnershipType == SALE && bitcoinTransactionType == Outgoing) ||
    (partnershipType == TRANSFER && bitcoinTransactionType == Outgoing)
}
