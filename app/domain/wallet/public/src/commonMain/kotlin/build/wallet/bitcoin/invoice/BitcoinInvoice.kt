package build.wallet.bitcoin.invoice

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.money.BitcoinMoney

/**
 * Represents a Bitcoin invoice used for sending and receiving onchain transactions.
 */
data class BitcoinInvoice(
  /** The address for the invoice. Required. */
  val address: BitcoinAddress,
  /** The decimal amount for the invoice specified in BTC denomination. Optional. */
  val amount: BitcoinMoney? = null,
  /** A label for the invoice. Optional. */
  val label: String? = null,
  /** A message for the invoice. Optional. */
  val message: String? = null,
  /** Custom parameters for the invoice. Optional. */
  val customParameters: Map<String, String>? = null,
)
