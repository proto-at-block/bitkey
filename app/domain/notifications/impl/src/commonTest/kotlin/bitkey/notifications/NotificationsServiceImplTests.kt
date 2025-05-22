package bitkey.notifications

import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.UsSmsFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.ktor.result.HttpError
import build.wallet.platform.permissions.PermissionCheckerMock
import build.wallet.platform.settings.TelephonyCountryCodeProviderMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

class NotificationsServiceImplTests : FunSpec({
  val permissionsChecker = PermissionCheckerMock()
  val notificationsPreferencesProvider = NotificationsPreferencesCachedProviderMock()
  val telephonyCountryCodeProvider = TelephonyCountryCodeProviderMock()

  val featureFlagDao = FeatureFlagDaoFake()
  val featureFlag = UsSmsFeatureFlag(featureFlagDao)
  val accountService = AccountServiceFake()

  val notificationService = NotificationsServiceImpl(
    permissionChecker = permissionsChecker,
    notificationsPreferencesProvider = notificationsPreferencesProvider,
    telephonyCountryCodeProvider = telephonyCountryCodeProvider,
    usSmsFeatureFlag = featureFlag,
    accountService = accountService
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
    permissionsChecker.permissionsOn = true
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
    permissionsChecker.permissionsOn = true
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
    permissionsChecker.permissionsOn = false
    val result = notificationService.getCriticalNotificationStatus().first()

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
    // Feature flag is false by default
    val result = notificationService.getCriticalNotificationStatus().first()

    // When in US with flag disabled, SMS should not be required
    result.shouldBe(NotificationsService.NotificationStatus.Enabled)
  }

  test("SMS required for US when feature flag enabled") {
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

    // Enable US SMS feature flag
    featureFlag.setFlagValue(true)

    val result = notificationService.getCriticalNotificationStatus().first()

    // With feature flag enabled, SMS should now be required for US users
    result.shouldBe(NotificationsService.NotificationStatus.Missing(setOf(NotificationChannel.Sms)))
  }

  test("SMS required for non-US regardless of feature flag") {
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
    telephonyCountryCodeProvider.mockCountryCode = "ca"
    // Feature flag doesn't matter for non-US users, but set to false to be sure
    featureFlag.setFlagValue(false)

    val result = notificationService.getCriticalNotificationStatus().first()

    // SMS should be required for non-US users regardless of feature flag
    result.shouldBe(NotificationsService.NotificationStatus.Missing(setOf(NotificationChannel.Sms)))
  }

  test("Preference Error") {
    val cause = HttpError.UnhandledException(RuntimeException())
    notificationsPreferencesProvider.notificationPreferences.value = Err(cause)
    permissionsChecker.permissionsOn = true
    telephonyCountryCodeProvider.mockCountryCode = "us"
    val result = notificationService.getCriticalNotificationStatus().first()

    result.shouldBe(NotificationsService.NotificationStatus.Error((cause)))
  }
})
