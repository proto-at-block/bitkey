package build.wallet.encrypt

import build.wallet.crypto.SymmetricKey
import build.wallet.crypto.SymmetricKeyImpl
import okio.ByteString
import okio.ByteString.Companion.toByteString
import build.wallet.rust.core.Hkdf as CoreHkdf

class HkdfImpl : Hkdf {
  override fun deriveKey(
    ikm: ByteString,
    salt: ByteString?,
    info: ByteString?,
    outputLength: Int,
  ): SymmetricKey {
    val hk =
      CoreHkdf(
        // Use a zero-filled byte sequence if salt is null (see RFC 5869, Section 2.2).
        // The length of the salt is set to 32 bytes to match the length of the output
        // of the SHA-256 hash function.
        salt?.toByteArray() ?: ByteArray(32),
        ikm.toByteArray()
      )
    val okm =
      hk.expand(
        // Use a zero-length byte sequence if info is null (see RFC 5869, Section 2.3).
        info?.toByteArray() ?: ByteArray(0),
        outputLength
      )
    return SymmetricKeyImpl(raw = okm.toByteString())
  }
}
