package build.wallet.cloud.backup.v2

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A [surrogate](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/serializers.md#composite-serializer-via-surrogate)
 * for holding a class that isn't otherwise annotated with [Serializable]. In this case,
 * [SpendingKeyset].
 *
 * Changing these field names will fail tests and then break cloud backups,
 * so proceed with caution.
 */
@Serializable
private data class SpendingKeysetSurrogate(
  val localId: String,
  val keysetServerId: String,
  val appDpub: String,
  val hardwareDpub: String,
  val serverDpub: String,
  val bitcoinNetworkType: BitcoinNetworkType,
)

object SpendingKeysetSerializer : KSerializer<SpendingKeyset> {
  override val descriptor: SerialDescriptor
    get() = SpendingKeysetSurrogate.serializer().descriptor

  override fun deserialize(decoder: Decoder): SpendingKeyset {
    val surrogate = decoder.decodeSerializableValue(SpendingKeysetSurrogate.serializer())
    return SpendingKeyset(
      localId = surrogate.localId,
      networkType = surrogate.bitcoinNetworkType,
      appKey = AppSpendingPublicKey(dpub = surrogate.appDpub),
      hardwareKey = HwSpendingPublicKey(dpub = surrogate.hardwareDpub),
      f8eSpendingKeyset =
        F8eSpendingKeyset(
          keysetId = surrogate.keysetServerId,
          spendingPublicKey = F8eSpendingPublicKey(dpub = surrogate.serverDpub)
        )
    )
  }

  override fun serialize(
    encoder: Encoder,
    value: SpendingKeyset,
  ) {
    val surrogate =
      SpendingKeysetSurrogate(
        localId = value.localId,
        keysetServerId = value.f8eSpendingKeyset.keysetId,
        appDpub = value.appKey.key.dpub,
        hardwareDpub = value.hardwareKey.key.dpub,
        serverDpub = value.f8eSpendingKeyset.spendingPublicKey.key.dpub,
        bitcoinNetworkType = value.networkType
      )
    encoder.encodeSerializableValue(SpendingKeysetSurrogate.serializer(), surrogate)
  }
}
