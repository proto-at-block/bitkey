package build.wallet.bitcoin.descriptor

import dev.zacsweers.redacted.annotations.Redacted

/**
 * Represents a Bitcoin descriptor with a common raw format.
 * This interface encapsulates descriptors used for different purposes in a Bitcoin wallet.
 */
sealed interface BitcoinDescriptor {
  val raw: String

  /**
   * Represents a Bitcoin spending descriptor.
   * This descriptor allows to spend Bitcoin.
   *
   * @property raw The raw representation of the spending descriptor.
   */
  @Redacted
  data class Spending(override val raw: String) : BitcoinDescriptor

  /**
   * Represents a Bitcoin watching descriptor.
   * Used to observe and monitor transactions without spending capabilities.
   *
   * @property raw The raw representation of the watching descriptor.
   */
  @Redacted
  data class Watching(override val raw: String) : BitcoinDescriptor
}
