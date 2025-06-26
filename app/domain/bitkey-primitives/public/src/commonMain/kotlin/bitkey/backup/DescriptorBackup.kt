package bitkey.backup

import bitkey.serialization.base64.ByteStringAsBase64Serializer
import build.wallet.bitkey.spending.SpendingKeyset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.ByteString

/**
 * Wallet descriptor encrypted with hardware. The backup is meant to be uploaded
 * and retrieved through f8e.
 *
 * @property keysetId id of a [SpendingKeyset] associated with a descriptor.
 * @property sealedDescriptor wallet descriptor encrypted with hardware.
 */
@Serializable
data class DescriptorBackup(
  @SerialName("keyset_id")
  val keysetId: String,
  @SerialName("sealed_descriptor")
  @Serializable(with = ByteStringAsBase64Serializer::class)
  val sealedDescriptor: ByteString,
)
