package build.wallet.f8e

import build.wallet.crypto.NoiseKeyVariant
import build.wallet.crypto.WsmIntegrityKeyVariant
import build.wallet.f8e.F8eEnvironment.Custom
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.f8e.F8eEnvironment.ForceOffline
import build.wallet.f8e.F8eEnvironment.Local
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.f8e.F8eEnvironment.Staging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = F8eEnvironmentSerializer::class)
sealed interface F8eEnvironment {
  /**
   * Environment that blocks all network requests.
   *
   * This is used in the Emergency Access Kit to prevent F8e calls from being made.
   */
  data object ForceOffline : F8eEnvironment

  data object Production : F8eEnvironment

  data object Staging : F8eEnvironment

  data object Development : F8eEnvironment

  data object Local : F8eEnvironment

  data class Custom(
    val url: String,
  ) : F8eEnvironment

  companion object {
    fun parseString(raw: String): F8eEnvironment =
      when (raw.lowercase()) {
        "production" -> Production
        "staging" -> Staging
        "development" -> Development
        "local" -> Local
        "force_offline" -> ForceOffline
        else -> Custom(raw)
      }
  }

  fun asString(): String =
    when (this) {
      Production -> "Production"
      Staging -> "Staging"
      Development -> "Development"
      Local -> "Local"
      ForceOffline -> "ForceOffline"
      is Custom -> url
    }
}

val F8eEnvironment.name: String
  get() =
    when (this) {
      Production -> "Production"
      Staging -> "Staging"
      Development -> "Development"
      Local -> "Local"
      is Custom -> "Custom"
      ForceOffline -> "ForceOffline"
    }

/**
 * Domain to use for accessing f8e endpoint.
 *
 * @property isAndroidEmulator used to determine local host IP
 */
fun F8eEnvironment.url(
  // TODO: inject
  isAndroidEmulator: Boolean,
): String =
  when (this) {
    Production -> "https://api.bitkey.world"
    Staging -> "https://api.bitkeystaging.com"
    Development -> "https://api.dev.wallet.build"
    // Android emulator puts the host device's localhost IP at 10.0.2.2
    // https://developer.android.com/studio/run/emulator-networking
    Local -> when {
      isAndroidEmulator -> "http://10.0.2.2:8080"
      else -> "http://127.0.0.1:8080"
    }
    is Custom -> url
    ForceOffline -> "https://offline.invalid"
  }

object F8eEnvironmentSerializer : KSerializer<F8eEnvironment> {
  override val descriptor: SerialDescriptor
    get() = PrimitiveSerialDescriptor("F8eEnvironment", PrimitiveKind.STRING)

  override fun deserialize(decoder: Decoder): F8eEnvironment =
    F8eEnvironment.parseString(decoder.decodeString())

  override fun serialize(
    encoder: Encoder,
    value: F8eEnvironment,
  ) {
    encoder.encodeString(value.asString())
  }
}

val F8eEnvironment.wsmIntegrityKeyVariant: WsmIntegrityKeyVariant
  get() =
    when (this) {
      Production -> WsmIntegrityKeyVariant.Prod
      else -> WsmIntegrityKeyVariant.Test
    }

val F8eEnvironment.noiseKeyVariant: NoiseKeyVariant
  get() =
    when (this) {
      Production -> NoiseKeyVariant.Prod
      else -> NoiseKeyVariant.Test
    }
