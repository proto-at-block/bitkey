package build.wallet.f8e.notifications

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.plugins.withAccountId
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.logging.withDescription
import build.wallet.ktor.result.*
import build.wallet.mapUnit
import com.github.michaelbull.result.Result
import io.ktor.client.request.put
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
class NotificationTriggerF8eClientImpl(
  private val f8eHttpClient: F8eHttpClient,
) : NotificationTriggerF8eClient {
  override suspend fun triggerNotification(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
    triggers: Set<NotificationTrigger>,
  ): Result<Unit, NetworkingError> {
    return f8eHttpClient.authenticated()
      .bodyResult<EmptyResponseBody> {
        put("/api/accounts/${accountId.serverId}/notifications/triggers") {
          withDescription("Setting notification triggers")
          withEnvironment(f8eEnvironment)
          withAccountId(accountId)
          setRedactedBody(PutNotificationTriggerRequest(triggers.toList()))
        }
      }
      .mapUnit()
  }
}

@Serializable
private data class PutNotificationTriggerRequest(
  @SerialName("notifications_triggers")
  val triggers: List<NotificationTrigger>,
) : RedactedRequestBody
