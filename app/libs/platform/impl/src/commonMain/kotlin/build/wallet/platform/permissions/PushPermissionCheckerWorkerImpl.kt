package build.wallet.platform.permissions

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState
import build.wallet.worker.RunStrategy
import kotlinx.coroutines.flow.filter

@BitkeyInject(AppScope::class)
class PushPermissionCheckerWorkerImpl(
  private val statusProvider: PushNotificationPermissionStatusProvider,
  private val permissionChecker: PermissionChecker,
  private val appSessionManager: AppSessionManager,
) : PushPermissionCheckerWorker {
  override val runStrategy: Set<RunStrategy>
    get() = setOf(
      RunStrategy.OnEvent(
        observer = appSessionManager.appSessionState.filter { it == AppSessionState.FOREGROUND }
      )
    )

  override suspend fun executeWork() {
    statusProvider.updatePushNotificationStatus(
      permissionChecker.getPermissionStatus(Permission.PushNotifications)
    )
  }
}
