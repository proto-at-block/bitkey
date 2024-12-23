package build.wallet.inheritance

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.inheritance.BeneficiaryClaim

/**
 * Details needed to confirm and complete an inheritance transaction.
 */
data class InheritanceTransactionDetails(
  /**
   * The claim for which the transaction is being completed.
   */
  val claim: BeneficiaryClaim.LockedClaim,
  /**
   * The wallet from which the inheritance will be sent.
   */
  val inheritanceWallet: SpendingWallet,
  /**
   * The address to send the inheritance to.
   */
  val recipientAddress: BitcoinAddress,
  /**
   * A partially signed transaction for the inheritance.
   *
   * This transaction is signed locally, but has not been broadcast to the
   * server for the second signature.
   */
  val psbt: Psbt,
)
