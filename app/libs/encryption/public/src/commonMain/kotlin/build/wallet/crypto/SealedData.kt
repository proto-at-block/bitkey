package build.wallet.crypto

import bitkey.serialization.hex.ByteStringAsHexSerializer
import kotlinx.serialization.Serializable
import okio.ByteString

/**
 * Type alias for [ByteString] representing sealed data.
 */
typealias SealedData =
  @Serializable(with = ByteStringAsHexSerializer::class)
  ByteString
