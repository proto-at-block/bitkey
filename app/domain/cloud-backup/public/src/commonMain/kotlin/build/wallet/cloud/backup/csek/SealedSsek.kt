package build.wallet.cloud.backup.csek

import bitkey.serialization.hex.ByteStringAsHexSerializer
import kotlinx.serialization.Serializable
import okio.ByteString

/**
 * Represents encrypted SSEK (Server Storage Encryption Key). Acts as an alias to access raw
 * [Ssek] from [SsekDao].
 *
 * Generally speaking, this key is created through the process of signing [Ssek] by the
 * hardware. Unlike [Ssek], this key is safe to pass around and keep in memory for as long
 * as needed.
 *
 * Note: using a typealias instead of a wrapped data class to preserve backwards compatibility with
 * older backups where sealed CSEK was encoded as an inlined field instead of wrapped object.
 */
typealias SealedSsek =
  @Serializable(with = ByteStringAsHexSerializer::class)
  ByteString
