package build.wallet.frost

/**
 * A public key...
 */
interface PublicKey {
  /**
   * Exports public key as a 33-byte hex string.
   */
  fun asString(): String
}
