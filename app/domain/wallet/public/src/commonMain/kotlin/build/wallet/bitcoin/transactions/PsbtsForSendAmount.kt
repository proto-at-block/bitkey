package build.wallet.bitcoin.transactions

import build.wallet.bitcoin.fees.Fee

/**
 * Contains the PSBTs for different transaction priorities when sending a specific amount.
 */
data class PsbtsForSendAmount(
  val fastest: Psbt?,
  val thirtyMinutes: Psbt?,
  val sixtyMinutes: Psbt,
) {
  /**
   * Returns a list of all fees from the available PSBTs.
   */
  fun fees(): List<Fee> {
    return listOfNotNull(
      fastest?.fee,
      thirtyMinutes?.fee,
      sixtyMinutes.fee
    )
  }

  /**
   * Checks if a PSBT exists for the given transaction priority.
   */
  operator fun contains(transactionPriority: EstimatedTransactionPriority): Boolean {
    return when (transactionPriority) {
      EstimatedTransactionPriority.FASTEST -> fastest != null
      EstimatedTransactionPriority.THIRTY_MINUTES -> thirtyMinutes != null
      EstimatedTransactionPriority.SIXTY_MINUTES -> true
    }
  }

  /**
   * Returns the PSBT for the given transaction priority, or null if not available.
   */
  operator fun get(transactionPriority: EstimatedTransactionPriority): Psbt? {
    return when (transactionPriority) {
      EstimatedTransactionPriority.FASTEST -> fastest
      EstimatedTransactionPriority.THIRTY_MINUTES -> thirtyMinutes
      EstimatedTransactionPriority.SIXTY_MINUTES -> sixtyMinutes
    }
  }
}
