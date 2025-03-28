package build.wallet.bitcoin.lightning

/**
 * Represents a BOLT 11 invoice to send/receive bitcoin transactions on Lightning.
 */
data class LightningInvoice(
  /**
   * Returns the payment recipient's public key.
   */
  val payeePubKey: String?,
  /**
   * Returns the hash to which we will receive the preimage upon completion of the payment.
   */
  val paymentHash: String?,
  /**
   * Returns whether the invoice has expired.
   */
  val isExpired: Boolean,
  /**
   * Returns the amount if specified in the invoice as millisatoshis.
   */
  val amountMsat: ULong?,
)
