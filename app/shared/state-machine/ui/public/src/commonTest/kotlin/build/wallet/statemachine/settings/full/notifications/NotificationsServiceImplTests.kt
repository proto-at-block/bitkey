package build.wallet.statemachine.settings.full.notifications

import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.HttpError
import build.wallet.notifications.NotificationChannel
import build.wallet.notifications.NotificationPreferences
import build.wallet.platform.permissions.PermissionCheckerMock
import build.wallet.platform.settings.TelephonyCountryCodeProviderMock
import build.wallet.statemachine.notifications.NotificationsPreferencesCachedProviderMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

class NotificationsServiceImplTests : FunSpec({
  val permissionsChecker = PermissionCheckerMock()
  val notificationsPreferencesProvider = NotificationsPreferencesCachedProviderMock()
  val telephonyCountryCodeProvider = TelephonyCountryCodeProviderMock()

  val notificationService = NotificationsServiceImpl(
    permissionChecker = permissionsChecker,
    notificationsPreferencesProvider = notificationsPreferencesProvider,
    telephonyCountryCodeProvider = telephonyCountryCodeProvider
  )

  test("Notifications Enabled") {
    notificationsPreferencesProvider.notificationPreferences.value = Ok(
      NotificationPreferences(
        accountSecurity = setOf(
          NotificationChannel.Sms,
          NotificationChannel.Push,
          NotificationChannel.Email
        ),
        moneyMovement = emptySet(),
        productMarketing = emptySet()
      )
    )
    permissionsChecker.permissionsOn = true
    val result = notificationService.getCriticalNotificationStatus(
      accountId = FullAccountIdMock,
      f8eEnvironment = F8eEnvironment.Development
    ).first()

    result.shouldBe(NotificationsService.NotificationStatus.Enabled)
  }

  test("Channels missing") {
    notificationsPreferencesProvider.notificationPreferences.value = Ok(
      NotificationPreferences(
        accountSecurity = emptySet(),
        moneyMovement = emptySet(),
        productMarketing = emptySet()
      )
    )
    permissionsChecker.permissionsOn = true
    val result = notificationService.getCriticalNotificationStatus(
      accountId = FullAccountIdMock,
      f8eEnvironment = F8eEnvironment.Development
    ).first()

    result.shouldBe(
      NotificationsService.NotificationStatus.Missing(
        setOf(NotificationChannel.Sms, NotificationChannel.Push, NotificationChannel.Email)
      )
    )
  }

  test("Push permission missing") {
    notificationsPreferencesProvider.notificationPreferences.value = Ok(
      NotificationPreferences(
        accountSecurity = setOf(
          NotificationChannel.Sms,
          NotificationChannel.Email
        ),
        moneyMovement = emptySet(),
        productMarketing = emptySet()
      )
    )
    permissionsChecker.permissionsOn = false
    val result = notificationService.getCriticalNotificationStatus(
      accountId = FullAccountIdMock,
      f8eEnvironment = F8eEnvironment.Development
    ).first()

    result.shouldBe(NotificationsService.NotificationStatus.Missing(setOf(NotificationChannel.Push)))
  }

  test("SMS missing in Unavailable Country code") {
    notificationsPreferencesProvider.notificationPreferences.value = Ok(
      NotificationPreferences(
        accountSecurity = setOf(
          NotificationChannel.Push,
          NotificationChannel.Email
        ),
        moneyMovement = emptySet(),
        productMarketing = emptySet()
      )
    )
    permissionsChecker.permissionsOn = true
    telephonyCountryCodeProvider.mockCountryCode = "us"
    val result = notificationService.getCriticalNotificationStatus(
      accountId = FullAccountIdMock,
      f8eEnvironment = F8eEnvironment.Development
    ).first()

    result.shouldBe(NotificationsService.NotificationStatus.Enabled)
  }

  test("Preference Error") {
    val cause = HttpError.UnhandledException(RuntimeException())
    notificationsPreferencesProvider.notificationPreferences.value = Err(cause)
    permissionsChecker.permissionsOn = true
    telephonyCountryCodeProvider.mockCountryCode = "us"
    val result = notificationService.getCriticalNotificationStatus(
      accountId = FullAccountIdMock,
      f8eEnvironment = F8eEnvironment.Development
    ).first()

    result.shouldBe(NotificationsService.NotificationStatus.Error((cause)))
  }
})
