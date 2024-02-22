package build.wallet.f8e.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.catching
import build.wallet.logging.logNetworkFailure
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

class UpdateDelayNotifyPeriodForTestingServiceImpl(
  private val f8eHttpClient: F8eHttpClient,
) : UpdateDelayNotifyPeriodForTestingService {
  override suspend fun updateDelayNotifyPeriodForTesting(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    delayNotifyDuration: Duration,
  ): Result<Unit, NetworkingError> {
    delayNotifyDuration.inWholeSeconds
    return f8eHttpClient.authenticated(f8eEnvironment, fullAccountId)
      .catching {
        put(urlString = "/api/accounts/${fullAccountId.serverId}/delay-notify/test") {
          setBody(
            Request(delayNotifyDuration.inWholeSeconds.toInt())
          )
        }
      }
      .logNetworkFailure { "Updating delay notify duration failed" }
      .mapUnit()
  }

  @Serializable
  private data class Request(
    @SerialName("delay_period_num_sec")
    val delayNotifyNumSec: Int,
  )
}
