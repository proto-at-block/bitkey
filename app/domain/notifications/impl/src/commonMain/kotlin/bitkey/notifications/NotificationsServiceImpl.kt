package bitkey.notifications

import build.wallet.account.AccountService
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.UsSmsFeatureFlag
import build.wallet.platform.permissions.PermissionStatus.Authorized
import build.wallet.platform.permissions.PushNotificationPermissionStatusProvider
import build.wallet.platform.settings.TelephonyCountryCodeProvider
import build.wallet.platform.settings.isCountry
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull

@BitkeyInject(AppScope::class)
class NotificationsServiceImpl(
  private val notificationsPreferencesProvider: NotificationsPreferencesCachedProvider,
  private val telephonyCountryCodeProvider: TelephonyCountryCodeProvider,
  private val usSmsFeatureFlag: UsSmsFeatureFlag,
  private val accountService: AccountService,
  private val pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider,
) : NotificationsService, NotificationsAppWorker {
  override fun getCriticalNotificationStatus(): Flow<NotificationsService.NotificationStatus> {
    return combine(
      accountService.activeAccount()
        .mapNotNull { it?.accountId },
      pushNotificationPermissionStatusProvider.pushNotificationStatus(),
      notificationsPreferencesProvider.getNotificationsPreferences(),
      usSmsFeatureFlag.flagValue()
    ) { _, notificationPermission, preferences, usSmsEnabledFlag ->
      val isUsCountryCode = telephonyCountryCodeProvider.isCountry("us")
      val usSmsEnabled = usSmsEnabledFlag.value
      val criticalChannels = setOfNotNull(
        // Show SMS as an option for US customers only if the feature flag is enabled
        NotificationChannel.Sms.takeIf { !isUsCountryCode || usSmsEnabled },
        NotificationChannel.Push,
        NotificationChannel.Email
      )
      val missingChannels = criticalChannels.minus(preferences?.get()?.accountSecurity.orEmpty())
        .let {
          if (notificationPermission != Authorized) {
            it + NotificationChannel.Push
          } else {
            it
          }
        }

      when {
        preferences?.isErr == true -> NotificationsService.NotificationStatus.Error(preferences.error)
        missingChannels.isNotEmpty() -> NotificationsService.NotificationStatus.Missing(
          missingChannels
        )
        notificationPermission != Authorized -> NotificationsService.NotificationStatus.Missing(
          setOf(NotificationChannel.Push)
        )
        else -> NotificationsService.NotificationStatus.Enabled
      }
    }
  }

  override suspend fun executeWork() {
    accountService.activeAccount()
      .collect { account ->
        if (account is FullAccount) {
          // Initialize the notifications preferences provider once there is an active full account
          notificationsPreferencesProvider.initialize()
        }
      }
  }
}
