package build.wallet.f8e.featureflags

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = F8eFeatureFlagValue.Serializer::class)
sealed interface F8eFeatureFlagValue {
  data class BooleanValue(val value: Boolean) : F8eFeatureFlagValue

  data class DoubleValue(val value: Double) : F8eFeatureFlagValue

  data class StringValue(val value: String) : F8eFeatureFlagValue

  object Serializer : KSerializer<F8eFeatureFlagValue> {
    private val surrogateSerializer = F8eFeatureFlagValueSurrogate.serializer()
    override val descriptor: SerialDescriptor = surrogateSerializer.descriptor

    override fun deserialize(decoder: Decoder): F8eFeatureFlagValue {
      val surrogate = surrogateSerializer.deserialize(decoder)

      return when {
        surrogate.boolean != null -> BooleanValue(surrogate.boolean)
        surrogate.double != null -> DoubleValue(surrogate.double)
        surrogate.string != null -> StringValue(surrogate.string)
        else -> throw SerializationException("Value is required")
      }
    }

    override fun serialize(
      encoder: Encoder,
      value: F8eFeatureFlagValue,
    ) {
      val surrogate =
        when (value) {
          is BooleanValue -> F8eFeatureFlagValueSurrogate(boolean = value.value)
          is DoubleValue -> F8eFeatureFlagValueSurrogate(double = value.value)
          is StringValue -> F8eFeatureFlagValueSurrogate(string = value.value)
        }
      surrogateSerializer.serialize(encoder, surrogate)
    }
  }
}

@Serializable
private class F8eFeatureFlagValueSurrogate(
  val boolean: Boolean? = null,
  val double: Double? = null,
  val string: String? = null,
) {
  init {
    require(listOfNotNull(boolean, double, string).size == 1) {
      "Only 1 value key is supported"
    }
  }
}
