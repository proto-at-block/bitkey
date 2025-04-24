package bitkey.notifications

import build.wallet.bitkey.f8e.AccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.UsSmsFeatureFlag
import build.wallet.platform.permissions.Permission
import build.wallet.platform.permissions.Permission.PushNotifications
import build.wallet.platform.permissions.PermissionChecker
import build.wallet.platform.permissions.PermissionStatus
import build.wallet.platform.permissions.PermissionStatus.Authorized
import build.wallet.platform.settings.TelephonyCountryCodeProvider
import build.wallet.platform.settings.isCountry
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@BitkeyInject(AppScope::class)
class NotificationsServiceImpl(
  private val permissionChecker: PermissionChecker,
  private val notificationsPreferencesProvider: NotificationsPreferencesCachedProvider,
  private val telephonyCountryCodeProvider: TelephonyCountryCodeProvider,
  private val usSmsFeatureFlag: UsSmsFeatureFlag,
) : NotificationsService {
  override fun getCriticalNotificationStatus(
    accountId: AccountId,
  ): Flow<NotificationsService.NotificationStatus> =
    combine(
      notificationsPreferencesProvider.getNotificationsPreferences(accountId),
      usSmsFeatureFlag.flagValue()
    ) { preferences, usSmsEnabledFlag ->
      val isUsCountryCode = telephonyCountryCodeProvider.isCountry("us")
      val usSmsEnabled = usSmsEnabledFlag.value
      val criticalChannels = setOfNotNull(
        // Show SMS as an option for US customers only if the feature flag is enabled
        NotificationChannel.Sms.takeIf { !isUsCountryCode || usSmsEnabled },
        NotificationChannel.Push,
        NotificationChannel.Email
      )
      val notificationPermission =
        permissionChecker.getPermissionStatus(Permission.PushNotifications)
      val missingChannels = criticalChannels
        .minus(preferences.get()?.accountSecurity.orEmpty())
        .let {
          if (notificationPermission != PermissionStatus.Authorized) {
            it + NotificationChannel.Push
          } else {
            it
          }
        }

      when {
        preferences.isErr -> NotificationsService.NotificationStatus.Error(preferences.error)
        missingChannels.isNotEmpty() -> NotificationsService.NotificationStatus.Missing(
          missingChannels
        )
        notificationPermission != PermissionStatus.Authorized -> NotificationsService.NotificationStatus.Missing(
          setOf(NotificationChannel.Push)
        )
        else -> NotificationsService.NotificationStatus.Enabled
      }
    }
}
