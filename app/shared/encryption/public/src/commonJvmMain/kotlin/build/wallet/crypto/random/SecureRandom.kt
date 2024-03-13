package build.wallet.crypto.random

actual class SecureRandom {
  private val random = java.security.SecureRandom()

  actual fun nextBytes(bytes: ByteArray): ByteArray = bytes.also { random.nextBytes(it) }
}
