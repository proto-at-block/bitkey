package bitkey.notifications

import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.ktor.result.HttpError
import build.wallet.platform.permissions.PermissionStatus
import build.wallet.platform.permissions.PushNotificationPermissionStatusProviderMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

class NotificationsServiceImplTests : FunSpec({
  val pushNotificationPermissionStatusProvider = PushNotificationPermissionStatusProviderMock()
  val notificationsPreferencesProvider = NotificationsPreferencesCachedProviderMock()

  val accountService = AccountServiceFake()

  val notificationService = NotificationsServiceImpl(
    notificationsPreferencesProvider = notificationsPreferencesProvider,
    accountService = accountService,
    pushNotificationPermissionStatusProvider = pushNotificationPermissionStatusProvider
  )

  beforeTest {
    accountService.setActiveAccount(FullAccountMock)
  }

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
    pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
      status = PermissionStatus.Authorized
    )
    val result = notificationService.getCriticalNotificationStatus().first()

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
    pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
      status = PermissionStatus.Authorized
    )
    val result = notificationService.getCriticalNotificationStatus().first()

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
    pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
      status = PermissionStatus.Denied
    )
    val result = notificationService.getCriticalNotificationStatus().first()

    result.shouldBe(NotificationsService.NotificationStatus.Missing(setOf(NotificationChannel.Push)))
  }

  test("SMS missing when not enabled") {
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
    pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
      status = PermissionStatus.Authorized
    )
    val result = notificationService.getCriticalNotificationStatus().first()

    // SMS is always a critical channel, so it should be reported missing
    result.shouldBe(NotificationsService.NotificationStatus.Missing(setOf(NotificationChannel.Sms)))
  }

  test("Preference Error") {
    val cause = HttpError.UnhandledException(RuntimeException())
    notificationsPreferencesProvider.notificationPreferences.value = Err(cause)
    pushNotificationPermissionStatusProvider.updatePushNotificationStatus(
      status = PermissionStatus.Authorized
    )
    val result = notificationService.getCriticalNotificationStatus().first()

    result.shouldBe(NotificationsService.NotificationStatus.Error((cause)))
  }
})
