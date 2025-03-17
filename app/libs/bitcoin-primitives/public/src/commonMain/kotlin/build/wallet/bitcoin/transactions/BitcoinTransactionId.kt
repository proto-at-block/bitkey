package build.wallet.bitcoin.transactions

import kotlin.jvm.JvmInline

/**
 * Represents a Bitcoin transaction ID.
 *
 * @param value string representation of the transaction ID.
 */
@JvmInline
value class BitcoinTransactionId(val value: String) {
  init {
    require(value.isNotBlank()) { "Transaction ID cannot be blank" }
  }

  /**
   * Returns a truncated version of the transaction ID.
   */
  fun truncated(): String = "${value.take(8)}...${value.takeLast(8)}"
}
