package bitkey.f8e.privilegedactions

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.ktor.result.*
import build.wallet.logging.logFailure
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@BitkeyInject(AppScope::class)
class ConfigureDelayDurationF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : ConfigureDelayDurationF8eClient {
  override suspend fun configureDelayDuration(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    privilegedActionId: String,
    delayDuration: Duration,
  ): Result<Unit, NetworkingError> =
    f8eHttpClient.authenticated()
      .bodyResult<EmptyResponseBody> {
        put("/api/accounts/${fullAccountId.serverId}/privileged-actions/$privilegedActionId/test") {
          withEnvironment(f8eEnvironment)
          withAccountId(fullAccountId)
          setRedactedBody(ConfigureDelayDurationRequest(delayDuration.inWholeMilliseconds))
        }
      }
      .logFailure {
        "Failed to configure delay duration for privileged action: $privilegedActionId"
      }
      .mapUnit()
}

/**
 * Request to configure delay duration for a privileged action
 */
@Serializable
data class ConfigureDelayDurationRequest(
  @SerialName("delay_duration")
  val delayDuration: Long,
) : RedactedRequestBody
