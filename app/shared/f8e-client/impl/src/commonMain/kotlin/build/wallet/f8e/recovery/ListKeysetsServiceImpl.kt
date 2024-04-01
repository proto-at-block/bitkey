package build.wallet.f8e.recovery

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.serialization.fromJsonString
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.logging.logNetworkFailure
import build.wallet.platform.random.UuidGenerator
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ListKeysetsServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
  private val uuidGenerator: UuidGenerator,
) : ListKeysetsService {
  override suspend fun listKeysets(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<List<SpendingKeyset>, NetworkingError> {
    return f8eHttpClient.authenticated(f8eEnvironment, fullAccountId)
      .bodyResult<ResponseBody> {
        get("/api/accounts/${fullAccountId.serverId}/keysets")
      }
      .map { body ->
        body.keysets.map { keyset ->
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
      }
      .logNetworkFailure { "Failed to get keysets from f8e" }
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
  )
}
