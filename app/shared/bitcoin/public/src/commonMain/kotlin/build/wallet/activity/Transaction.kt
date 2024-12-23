package build.wallet.activity

import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.*
import build.wallet.money.BitcoinMoney

/**
 * Represents a transaction that belongs to an active account.
 *
 * All transactions activity can be accessed by [TransactionsActivityService].
 */
sealed interface Transaction {
  val id: String

  /**
   * Represents a Bitcoin wallet on-chain transaction. When [Transaction.BitcoinWalletTransaction] is emitted,
   * it indicates that there is no associated partnership transaction that we know of.
   */
  data class BitcoinWalletTransaction(
    val details: BitcoinTransaction,
  ) : Transaction {
    override val id: String = details.id
  }

  /**
   * Represents a partnership transaction. When [Transaction.PartnershipTransaction] is emitted,
   * it indicates that there is an expected or completed partnership transaction (depending on status),
   * which may or may not be associated to an actual [Transaction.BitcoinWalletTransaction].
   */
  data class PartnershipTransaction(
    val details: build.wallet.partnerships.PartnershipTransaction,
    val bitcoinTransaction: BitcoinTransaction?,
  ) : Transaction {
    override val id: String = details.id.value
  }
}

fun Transaction.PartnershipTransaction.bitcoinTotal(): BitcoinMoney? {
  return when (val transaction = bitcoinTransaction) {
    null -> details.cryptoAmount?.let { BitcoinMoney.btc(it) }
    else -> when (transaction.transactionType) {
      Incoming -> transaction.subtotal
      Outgoing, UtxoConsolidation -> transaction.total
    }
  }
}

/**
 * The [BitcoinTransaction] associated with the transaction, if there is one.
 */
fun Transaction.onChainDetails() =
  when (this) {
    is Transaction.BitcoinWalletTransaction -> details
    is Transaction.PartnershipTransaction -> bitcoinTransaction
  }
