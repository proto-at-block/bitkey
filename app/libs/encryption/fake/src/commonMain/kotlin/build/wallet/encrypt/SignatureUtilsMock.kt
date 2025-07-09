package build.wallet.encrypt

import okio.ByteString
import okio.ByteString.Companion.toByteString

class SignatureUtilsMock : SignatureUtils {
  override fun encodeSignatureToDer(compactSignature: ByteArray): ByteString {
    return ByteArray(70) { 0xAB.toByte() }.toByteString()
  }

  override fun decodeSignatureFromDer(derSignature: ByteString): ByteArray {
    return ByteArray(64) { 0xCD.toByte() }
  }
}
