package build.wallet.f8e.notifications

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

/**
 * F8e client for interacting with the notification trigger service. The app will describe a state
 * it is in and f8e will appropriately send notifications based on policy
 */
interface NotificationTriggerF8eClient {
  /**
   * Send the current active [NotificationTrigger]s to f8e to trigger the relevant notification policy
   * When [triggers] is empty, there is nothing to notify and all policies are concluded
   */
  suspend fun triggerNotification(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
    triggers: Set<NotificationTrigger>,
  ): Result<Unit, NetworkingError>
}

enum class NotificationTrigger {
  SECURITY_HUB_WALLET_AT_RISK,
}
