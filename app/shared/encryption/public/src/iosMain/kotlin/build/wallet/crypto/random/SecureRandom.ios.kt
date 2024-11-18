package build.wallet.crypto.random

import kotlinx.cinterop.refTo
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

/**
 * See https://developer.apple.com/documentation/security/1399291-secrandomcopybytes
 */
actual class SecureRandom {
  actual fun nextBytes(bytes: ByteArray): ByteArray {
    val status = SecRandomCopyBytes(
      kSecRandomDefault,
      bytes.size.toULong(),
      bytes.refTo(0)
    )
    require(status == errSecSuccess) { "Failed to generate random bytes" }
    return bytes
  }
}
