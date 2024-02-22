package build.wallet.encrypt

import okio.ByteString.Companion.toByteString
import java.security.SecureRandom

class XNonceGeneratorImpl : XNonceGenerator {
  private val random = SecureRandom()

  override fun generateXNonce(): XNonce {
    val randomBytes = ByteArray(24)
    random.nextBytes(randomBytes)
    return XNonce(randomBytes.toByteString())
  }
}
