package build.wallet.encrypt

import build.wallet.serialization.ByteStringAsHexSerializer
import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.serialization.Serializable
import okio.ByteString

/**
 * @property tag - currently used purely for iOS interop. On Android this is set to [ByteString.EMPTY].
 * TODO(W-644): harden symmetric encryption approach.
 */
@Redacted
@Serializable
data class SealedData(
  @Serializable(with = ByteStringAsHexSerializer::class)
  val ciphertext: ByteString,
  @Serializable(with = ByteStringAsHexSerializer::class)
  val nonce: ByteString,
  @Serializable(with = ByteStringAsHexSerializer::class)
  val tag: ByteString,
)
