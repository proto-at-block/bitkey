package bitkey.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Delegates kotlin serialization to a delegate type for simpler encoding. */
abstract class DelegateSerializer<SERIALIZED, DESERIALIZED>(
  private val delegate: KSerializer<SERIALIZED>,
) : KSerializer<DESERIALIZED> {
  final override val descriptor: SerialDescriptor = delegate.descriptor

  final override fun deserialize(decoder: Decoder): DESERIALIZED =
    delegate.deserialize(
      decoder
    ).let(::deserialize)

  final override fun serialize(
    encoder: Encoder,
    value: DESERIALIZED,
  ) = delegate.serialize(
    encoder,
    serialize(value)
  )

  protected abstract fun serialize(data: DESERIALIZED): SERIALIZED

  protected abstract fun deserialize(data: SERIALIZED): DESERIALIZED
}
