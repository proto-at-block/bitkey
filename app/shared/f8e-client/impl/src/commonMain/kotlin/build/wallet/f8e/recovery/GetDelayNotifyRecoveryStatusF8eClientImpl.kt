package build.wallet.f8e.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.HttpError.UnhandledException
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.catching
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class GetDelayNotifyRecoveryStatusF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : GetDelayNotifyRecoveryStatusF8eClient {
  override suspend fun getStatus(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<ServerRecovery?, NetworkingError> =
    coroutineBinding {
      val response =
        f8eHttpClient.authenticated(f8eEnvironment, fullAccountId)
          .catching {
            get("/api/accounts/${fullAccountId.serverId}/recovery") {
              withDescription("Get delay & notify status")
            }
          }
          .bind()

      val body = response.bodyResult<GetDelayNotifyStatusResponse>().bind()
      body.pendingDelayNotify?.run {
        toServerRecovery(fullAccountId).mapError(::UnhandledException).bind()
      }
    }

  @Serializable
  private data class GetDelayNotifyStatusResponse(
    @SerialName("pending_delay_notify")
    val pendingDelayNotify: ServerResponseBody?,
    @SerialName("active_contest")
    val activeContest: Boolean,
  )
}
