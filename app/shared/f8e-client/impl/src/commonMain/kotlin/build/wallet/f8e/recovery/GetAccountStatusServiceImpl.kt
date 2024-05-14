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
import build.wallet.f8e.logging.withDescription
import build.wallet.f8e.recovery.GetAccountStatusService.AccountStatus
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.platform.random.UuidGenerator
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class GetAccountStatusServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
  private val uuidGenerator: UuidGenerator,
) : GetAccountStatusService {
  override suspend fun status(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    networkType: BitcoinNetworkType,
  ): Result<AccountStatus, NetworkingError> {
    return f8eHttpClient.authenticated(f8eEnvironment, fullAccountId)
      .bodyResult<ResponseBody> {
        get("/api/accounts/${fullAccountId.serverId}") {
          withDescription("Get account status from f8e")
        }
      }
      .map { body ->
        val appBitcoinPublicKey = AppSpendingPublicKey(dpub = body.spending.appDpub)
        val hardwareBitcoinPublicKey = HwSpendingPublicKey(dpub = body.spending.hardwareDpub)
        val serverBitcoinPublicKey = F8eSpendingPublicKey(dpub = body.spending.serverDpub)
        return Ok(
          AccountStatus(
            sourceServerSpendingKeysetId = body.sourceServerSpendingKeysetId,
            spendingKeyset =
              SpendingKeyset(
                localId = uuidGenerator.random(),
                f8eSpendingKeyset =
                  F8eSpendingKeyset(
                    keysetId = body.sourceServerSpendingKeysetId,
                    spendingPublicKey = serverBitcoinPublicKey
                  ),
                networkType = networkType,
                appKey = appBitcoinPublicKey,
                hardwareKey = hardwareBitcoinPublicKey
              )
          )
        )
      }
  }

  @Serializable
  private data class Spending(
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
    val sourceServerSpendingKeysetId: String,
    @SerialName("spending")
    val spending: Spending,
  ) : RedactedResponseBody
}
