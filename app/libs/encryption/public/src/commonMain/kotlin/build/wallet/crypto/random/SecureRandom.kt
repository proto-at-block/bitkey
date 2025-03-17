package build.wallet.crypto.random

/**
 * A cryptographically secure random number generator backed by platform-specific implementations.
 *
 * Android/JVM: [java.security.SecureRandom]
 * iOS: [Security.SecRandomCopyBytes]
 */
expect class SecureRandom() {
/**
   * Fills the specified byte [array] with random bytes and returns it.
   *
   * @return [array] filled with random bytes.
   */
  fun nextBytes(bytes: ByteArray): ByteArray
}

/**
 * Creates a byte array of the specified [size], filled with random bytes.
 */
fun SecureRandom.nextBytes(size: Int): ByteArray = nextBytes(ByteArray(size))
