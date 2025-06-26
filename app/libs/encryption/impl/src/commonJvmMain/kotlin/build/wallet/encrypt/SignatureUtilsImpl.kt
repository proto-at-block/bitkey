package build.wallet.encrypt

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.rust.core.SignatureUtilsException
import okio.ByteString
import okio.ByteString.Companion.toByteString
import build.wallet.rust.core.encodeSignatureToDer as rustEncodeSignatureToDer

@BitkeyInject(AppScope::class)
class SignatureUtilsImpl : SignatureUtils {
  override fun encodeSignatureToDer(compactSignature: ByteArray): ByteString {
    return try {
      val result = rustEncodeSignatureToDer(compactSignature)
      result.map { it.toByte() }.toByteArray().toByteString()
    } catch (e: SignatureUtilsException.InvalidCompactSignature) {
      throw IllegalArgumentException("Failed to encode signature to DER format: ${e.message}", e)
    }
  }
}
