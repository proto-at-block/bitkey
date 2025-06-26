package build.wallet.f8e.notifications

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class NotificationTriggerF8eClientFake(
  val triggerNotificationResponse: Result<Unit, NetworkingError> = Ok(Unit),
) : NotificationTriggerF8eClient {
  var triggers: MutableSet<NotificationTrigger>? = null

  override suspend fun triggerNotification(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
    triggers: Set<NotificationTrigger>,
  ): Result<Unit, NetworkingError> {
    this.triggers = triggers.toMutableSet()
    return triggerNotificationResponse
  }
}
