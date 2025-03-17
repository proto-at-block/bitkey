package build.wallet.encrypt

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import okio.ByteString.Companion.toByteString
import java.security.SecureRandom

@BitkeyInject(AppScope::class)
class XNonceGeneratorImpl : XNonceGenerator {
  private val random = SecureRandom()

  override fun generateXNonce(): XNonce {
    val randomBytes = ByteArray(24)
    random.nextBytes(randomBytes)
    return XNonce(randomBytes.toByteString())
  }
}
