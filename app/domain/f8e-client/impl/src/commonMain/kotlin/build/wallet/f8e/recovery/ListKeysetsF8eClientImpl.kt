package build.wallet.f8e.recovery

import bitkey.backup.DescriptorBackup
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.serialization.fromJsonString
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.platform.random.UuidGenerator
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class ListKeysetsF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
  private val uuidGenerator: UuidGenerator,
) : ListKeysetsF8eClient {
  override suspend fun listKeysets(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<ListKeysetsF8eClient.ListKeysetsResponse, NetworkingError> {
    return f8eHttpClient.authenticated()
      .bodyResult<ResponseBody> {
        get("/api/accounts/${fullAccountId.serverId}/keysets") {
          withDescription("Get keysets from f8e")
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
        }
      }
      .map { body ->
        val keysets = body.keysets.map { keyset ->
          val appBitcoinPublicKey = AppSpendingPublicKey(dpub = keyset.appDpub)
          val hardwareBitcoinPublicKey = HwSpendingPublicKey(dpub = keyset.hardwareDpub)
          val serverBitcoinPublicKey = F8eSpendingPublicKey(dpub = keyset.serverDpub)
          SpendingKeyset(
            localId = uuidGenerator.random(),
            f8eSpendingKeyset =
              F8eSpendingKeyset(
                keysetId = keyset.keysetId,
                spendingPublicKey = serverBitcoinPublicKey
              ),
            networkType = BitcoinNetworkType.fromJsonString(keyset.networkType),
            appKey = appBitcoinPublicKey,
            hardwareKey = hardwareBitcoinPublicKey
          )
        }

        ListKeysetsF8eClient.ListKeysetsResponse(
          keysets = keysets,
          wrappedSsek = body.wrappedSsek,
          descriptorBackups = body.descriptorBackups
        )
      }
  }

  @Serializable
  private data class Keyset(
    @SerialName("keyset_id")
    val keysetId: String,
    @SerialName("network")
    val networkType: String,
    @SerialName("app_dpub")
    val appDpub: String,
    @SerialName("hardware_dpub")
    val hardwareDpub: String,
    @SerialName("server_dpub")
    val serverDpub: String,
  )

  @Serializable
  private data class ResponseBody(
    @SerialName("keysets")
    val keysets: List<Keyset>,
    @SerialName("wrapped_ssek")
    val wrappedSsek: SealedSsek?,
    @SerialName("descriptor_backups")
    val descriptorBackups: List<DescriptorBackup>,
  ) : RedactedResponseBody
}
