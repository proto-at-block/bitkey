package build.wallet.encrypt

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.rust.core.SignatureUtilsException
import build.wallet.toUByteList
import okio.ByteString
import okio.ByteString.Companion.toByteString
import build.wallet.rust.core.compactSignatureFromDer as rustCompactSignatureFromDer
import build.wallet.rust.core.compactSignatureToDer as rustCompactSignatureToDer

@BitkeyInject(AppScope::class)
class SignatureUtilsImpl : SignatureUtils {
  override fun encodeSignatureToDer(compactSignature: ByteArray): ByteString {
    return try {
      val result = rustCompactSignatureToDer(compactSignature.toUByteList())
      result.map { it.toByte() }.toByteArray().toByteString()
    } catch (e: SignatureUtilsException.InvalidCompactSignature) {
      throw IllegalArgumentException("Failed to encode signature to DER format: ${e.message}", e)
    }
  }

  override fun decodeSignatureFromDer(derSignature: ByteString): ByteArray {
    return try {
      val bytes = derSignature.toByteArray()
      val result = rustCompactSignatureFromDer(bytes.toUByteList())
      result.map { it.toByte() }.toByteArray()
    } catch (e: SignatureUtilsException.InvalidDerSignature) {
      throw IllegalArgumentException("Failed to decode DER signature to compact format: ${e.message}", e)
    }
  }
}
