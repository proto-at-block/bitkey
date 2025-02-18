package build.wallet.crypto

import build.wallet.serialization.ByteStringAsHexSerializer
import kotlinx.serialization.Serializable
import okio.ByteString

/**
 * Type alias for [ByteString] representing sealed data.
 */
typealias SealedData =
  @Serializable(with = ByteStringAsHexSerializer::class)
  ByteString
