package build.wallet.platform.random

fun interface Uuid {
  /**
   * Generates a new random Universally Unique Identifier (UUID) and returns hexadecimal
   * representation.
   */
  fun random(): String
}
