package bitkey.serialization.hex

import bitkey.serialization.DelegateSerializer
import kotlinx.serialization.builtins.serializer
import okio.ByteString
import okio.ByteString.Companion.decodeHex

/** Delegates kotlin serialization to a backing type for simpler encoding. */
object ByteStringAsHexSerializer : DelegateSerializer<String, ByteString>(String.serializer()) {
  override fun serialize(data: ByteString): String = data.hex()

  override fun deserialize(data: String): ByteString = data.decodeHex()
}
