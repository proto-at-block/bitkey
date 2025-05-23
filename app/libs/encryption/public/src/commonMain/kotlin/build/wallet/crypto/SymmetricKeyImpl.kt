package build.wallet.crypto

import bitkey.serialization.hex.ByteStringAsHexSerializer
import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.serialization.Serializable
import okio.ByteString

/**
 * Represents a symmetric key generated by [SymmetricKeyGenerator]. Used for sealing/unsealing data
 * by [SymmetricKeyEncryptor].
 *
 * TODO(W-644): should we avoid exposing raw key when passing it around? Alternative: persist key
 *  in local encrypted storage, use key hash as a key reference to access it.
 */
@Redacted
@Serializable
data class SymmetricKeyImpl(
  @Serializable(with = ByteStringAsHexSerializer::class)
  override val raw: ByteString,
) : SymmetricKey {
  companion object {
    // We actually use XChaCha20Poly1305; this is just for javax.crypto.KeyGenerator.
    const val ALGORITHM = "AES"
  }

  override val length: Int
    get() = raw.size
}
