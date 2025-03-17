package build.wallet.bitcoin.invoice

import build.wallet.bitcoin.lightning.LightningInvoice

/**
 * Data structure representing information we would expect to retrieve from BIP21 URIs.
 */
data class BIP21PaymentData(
  /**
   * Information about an onchain payment given by the standard BIP21 encoding.
   */
  val onchainInvoice: BitcoinInvoice,
  /**
   * Information about a lightning invoice, that is tagged along with the lightning URI parameter
   * in BIP 21.
   */
  val lightningInvoice: LightningInvoice?,
)
