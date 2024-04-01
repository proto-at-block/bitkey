package build.wallet.platform.random

/**
 * Use this interface over [uuid] when you need to inject the dependency for testing purposes.
 */
fun interface UuidGenerator {
  /**
   * Generates a new random Universally Unique Identifier (UUID) and returns hexadecimal
   * representation.
   */
  fun random(): String
}

/**
 * Generates a new random Universally Unique Identifier (UUID) and returns hexadecimal
 * representation.
 *
 * Use this ove [UuidGenerator] when you don't need to inject the dependency for
 * testing purposes.
 */
expect fun uuid(): String
