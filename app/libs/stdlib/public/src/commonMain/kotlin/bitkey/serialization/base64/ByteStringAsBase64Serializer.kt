package bitkey.serialization.base64

import bitkey.serialization.DelegateSerializer
import kotlinx.serialization.builtins.serializer
import okio.ByteString
import okio.ByteString.Companion.decodeBase64

object ByteStringAsBase64Serializer : DelegateSerializer<String, ByteString>(String.serializer()) {
  override fun serialize(data: ByteString): String = data.base64()

  override fun deserialize(data: String): ByteString =
    data.decodeBase64() ?: throw IllegalArgumentException("Invalid Base64 ByteString: $data")
}
