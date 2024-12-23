package build.wallet.platform.permissions

import android.app.Application
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.permissions.Permission.PushNotifications
import build.wallet.platform.permissions.PermissionStatus.Authorized
import build.wallet.platform.permissions.PermissionStatus.NotDetermined
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@BitkeyInject(AppScope::class)
class PushNotificationPermissionStatusProviderImpl(
  application: Application,
) : PushNotificationPermissionStatusProvider {
  private val statusFlow =
    MutableStateFlow(
      value =
        when (val manifestPermission = PushNotifications.manifestPermission()) {
          null -> Authorized
          else ->
            when (
              ContextCompat.checkSelfPermission(
                application,
                manifestPermission
              )
            ) {
              PERMISSION_GRANTED -> Authorized
              else -> NotDetermined
            }
        }
    )

  override fun pushNotificationStatus(): StateFlow<PermissionStatus> = statusFlow

  override fun updatePushNotificationStatus(status: PermissionStatus) {
    statusFlow.tryEmit(status)
  }
}
