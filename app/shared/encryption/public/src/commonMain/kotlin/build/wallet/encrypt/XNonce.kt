package build.wallet.encrypt

import build.wallet.serialization.ByteStringAsHexSerializer
import kotlinx.serialization.Serializable
import okio.ByteString

/**
 * A 24-byte nonce to be used with XChaCha20.
 */
@Serializable
data class XNonce(
  @Serializable(with = ByteStringAsHexSerializer::class)
  val bytes: ByteString,
)
