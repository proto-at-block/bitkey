package bitkey.f8e.account

import bitkey.backup.DescriptorBackup
import bitkey.serialization.base64.ByteStringAsBase64Serializer
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
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
import okio.ByteString

@BitkeyInject(AppScope::class)
class GetActiveKeysetF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
  private val uuidGenerator: UuidGenerator,
) : GetActiveKeysetF8eClient {
  override suspend fun get(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
  ): Result<GetActiveKeysetF8eClient.GetKeysetsResponse, NetworkingError> {
    return f8eHttpClient.authenticated()
      .bodyResult<ResponseBody> {
        get("/api/accounts/${accountId.serverId}") {
          withDescription("Get active keyset from f8e")
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
        }
      }
      .map { body ->
        val appBitcoinPublicKey = AppSpendingPublicKey(dpub = body.spendingKeyset.appDpub)
        val hardwareBitcoinPublicKey = HwSpendingPublicKey(dpub = body.spendingKeyset.hardwareDpub)
        val serverBitcoinPublicKey = F8eSpendingPublicKey(dpub = body.spendingKeyset.serverDpub)
        val spendingKeyset = SpendingKeyset(
          localId = uuidGenerator.random(),
          f8eSpendingKeyset = F8eSpendingKeyset(
            keysetId = body.keysetId,
            spendingPublicKey = serverBitcoinPublicKey
          ),
          networkType = BitcoinNetworkType.fromJsonString(body.spendingKeyset.networkType),
          appKey = appBitcoinPublicKey,
          hardwareKey = hardwareBitcoinPublicKey
        )
        GetActiveKeysetF8eClient.GetKeysetsResponse(
          keyset = spendingKeyset,
          descriptorBackup = body.sealedDescriptor?.let {
            DescriptorBackup(
              keysetId = body.keysetId,
              sealedDescriptor = it
            )
          }
        )
      }
  }

  @Serializable
  private data class SpendingKeyset(
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
    @SerialName("keyset_id")
    val keysetId: String,
    @SerialName("spending")
    val spendingKeyset: SpendingKeyset,
    @SerialName("sealed_descriptor")
    @Serializable(with = ByteStringAsBase64Serializer::class)
    val sealedDescriptor: ByteString?,
  ) : RedactedResponseBody
}
