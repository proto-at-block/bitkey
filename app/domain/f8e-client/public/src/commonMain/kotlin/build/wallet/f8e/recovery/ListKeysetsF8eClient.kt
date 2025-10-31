package build.wallet.f8e.recovery

import bitkey.backup.DescriptorBackup
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.REGTEST
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.platform.random.UuidGenerator
import com.github.michaelbull.result.Result
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Retrieves all spending keysets for a given account from the f8e server.
 * This API is intended for non-private (legacy/public-descriptor) wallets.
 * Private multisig wallets may return a different payload shape, but this client
 * is primarily used to enumerate server-known keysets for standard, non-private wallet accounts.
 */
interface ListKeysetsF8eClient {
  suspend fun listKeysets(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<ListKeysetsResponse, NetworkingError>
}

data class ListKeysetsResponse(
  val keysets: List<RemoteKeyset>,
  val wrappedSsek: SealedSsek?,
  val descriptorBackups: List<DescriptorBackup>,
)

/**
 * Polymorphic container to encapsulate both LegacyRemoteKeyset and PrivateMultisigRemoteKeyset
 */
@Serializable(with = RemoteKeysetSerializer::class)
sealed interface RemoteKeyset {
  val keysetId: String
  val networkType: String
}

/**
 * Coercive cast from Remote -> LegacyRemoteKeyset
 * You almost certainly don't want this, it will crash when used with PrivateMultisigRemoteKeyset
 */
fun List<RemoteKeyset>.toSpendingKeysets(uuidGenerator: UuidGenerator) =
  this.map {
    (it as LegacyRemoteKeyset).toSpendingKeyset(uuidGenerator)
  }

@Serializable
data class LegacyRemoteKeyset(
  @SerialName("keyset_id")
  override val keysetId: String,
  @SerialName("network")
  override val networkType: String,
  @SerialName("app_dpub")
  val appDescriptor: String,
  @SerialName("hardware_dpub")
  val hardwareDescriptor: String,
  @SerialName("server_dpub")
  val serverDescriptor: String,
) : RemoteKeyset {
  fun toSpendingKeyset(uuidGenerator: UuidGenerator): SpendingKeyset {
    val appBitcoinPublicKey = AppSpendingPublicKey(dpub = appDescriptor)
    val hardwareBitcoinPublicKey = HwSpendingPublicKey(dpub = hardwareDescriptor)
    val serverBitcoinPublicKey = F8eSpendingPublicKey(dpub = serverDescriptor)

    return SpendingKeyset(
      localId = uuidGenerator.random(),
      f8eSpendingKeyset =
        F8eSpendingKeyset(
          keysetId = keysetId,
          spendingPublicKey = serverBitcoinPublicKey,
          privateWalletRootXpub = null // does not exist for legacy keysets
        ),
      networkType = BitcoinNetworkType.fromJsonString(networkType),
      appKey = appBitcoinPublicKey,
      hardwareKey = hardwareBitcoinPublicKey
    )
  }
}

@Serializable
data class PrivateMultisigRemoteKeyset(
  @SerialName("keyset_id")
  override val keysetId: String,
  @SerialName("network")
  override val networkType: String,
  @SerialName("app_pub")
  val appPublicKey: String,
  @SerialName("hardware_pub")
  val hardwarePublicKey: String,
  @SerialName("server_pub")
  val serverPublicKey: String,
) : RemoteKeyset

private object RemoteKeysetSerializer : JsonContentPolymorphicSerializer<RemoteKeyset>(RemoteKeyset::class) {
  override fun selectDeserializer(element: JsonElement) =
    when {
      element.jsonObject.containsKey("app_dpub") -> LegacyRemoteKeyset.serializer()
      element.jsonObject.containsKey("app_pub") -> PrivateMultisigRemoteKeyset.serializer()
      else -> error("Unsupported keyset payload shape: ${element.jsonObject.keys}")
    }
}

private fun BitcoinNetworkType.Companion.fromJsonString(value: String): BitcoinNetworkType {
  return when (value) {
    "bitcoin-regtest" -> REGTEST // TODO: remove this edge case once W-11495 is fixed
    else -> BitcoinNetworkType.valueOf(value.uppercase())
  }
}
