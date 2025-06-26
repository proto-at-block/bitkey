package build.wallet.encrypt

import okio.ByteString
import okio.ByteString.Companion.toByteString

class SignatureUtilsMock : SignatureUtils {
  override fun encodeSignatureToDer(compactSignature: ByteArray): ByteString {
    require(compactSignature.size == 64) {
      "Compact signature must be 64 bytes, got ${compactSignature.size}"
    }

    val r = compactSignature.sliceArray(0..31)
    val s = compactSignature.sliceArray(32..63)

    // Simple mock implementation - just concatenate r and s with DER header for testing
    val mockDer = byteArrayOf(0x30, 0x44) +
      byteArrayOf(0x02, 0x20) + r +
      byteArrayOf(0x02, 0x20) + s
    return mockDer.toByteString()
  }
}
