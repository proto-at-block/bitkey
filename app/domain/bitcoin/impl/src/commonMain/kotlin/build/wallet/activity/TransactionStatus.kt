package build.wallet.activity

import build.wallet.activity.Transaction.BitcoinWalletTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.partnerships.PartnershipTransaction
import build.wallet.partnerships.PartnershipTransactionStatus.*

/**
 * A common transaction status between [PartnershipTransaction] and [BitcoinTransaction].
 */
enum class TransactionStatus {
  PENDING,
  CONFIRMED,
  FAILED,
}

fun Transaction.status() =
  when (this) {
    is BitcoinWalletTransaction -> details.status()
    is Transaction.PartnershipTransaction -> details.status()
  }

fun PartnershipTransaction.status(): TransactionStatus =
  when (this.status) {
    null, PENDING -> TransactionStatus.PENDING
    SUCCESS -> TransactionStatus.CONFIRMED
    FAILED -> TransactionStatus.FAILED
  }

fun BitcoinTransaction.status(): TransactionStatus =
  when (this.confirmationStatus) {
    Pending -> TransactionStatus.PENDING
    is Confirmed -> TransactionStatus.CONFIRMED
  }
