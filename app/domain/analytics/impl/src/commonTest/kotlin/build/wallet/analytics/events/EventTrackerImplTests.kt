package build.wallet.analytics.events

import bitkey.account.AccountConfigServiceFake
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus
import build.wallet.account.analytics.AppInstallationDaoMock
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_APP_ACCOUNT_CREATED
import build.wallet.analytics.v1.Action.ACTION_APP_SCREEN_IMPRESSION
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryMock
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.device.DevicePlatform.Android
import build.wallet.platform.device.DevicePlatform.IOS
import build.wallet.platform.settings.LocaleCountryCodeProviderMock
import build.wallet.platform.settings.LocaleCurrencyCodeProviderFake
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

class EventTrackerImplTests : FunSpec({
  val sessionId = "session-id"
  val clock = ClockFake()
  val countryCodeProvider = LocaleCountryCodeProviderMock()
  val appInstallationDao = AppInstallationDaoMock()
  val hardWareInfoProvider = HardwareInfoProviderMock()
  val platformInfoProvider = PlatformInfoProviderMock()
  val appDeviceIdDao = AppDeviceIdDaoMock()
  val deviceInfoProvider = DeviceInfoProviderMock()
  val accountService = AccountServiceFake()
  val eventStore = EventStoreMock()
  val appConfigService = AccountConfigServiceFake()
  val analyticsTrackingPreference = AnalyticsTrackingPreferenceFake()
  val analyticsEventQueue = AnalyticsEventQueueFake()

  val appScope = TestScope()

  val eventTracker = EventTrackerImpl(
    appCoroutineScope = appScope,
    appDeviceIdDao = appDeviceIdDao,
    deviceInfoProvider = deviceInfoProvider,
    accountService = accountService,
    accountConfigService = appConfigService,
    clock = clock,
    countryCodeProvider = countryCodeProvider,
    eventQueue = analyticsEventQueue,
    appInstallationDao = appInstallationDao,
    hardwareInfoProvider = hardWareInfoProvider,
    platformInfoProvider = platformInfoProvider,
    appSessionManager = AppSessionManagerFake(sessionId),
    eventStore = eventStore,
    bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryMock(),
    fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create),
    localeCurrencyCodeProvider = LocaleCurrencyCodeProviderFake(),
    analyticsTrackingPreference = analyticsTrackingPreference
  )

  beforeTest {
    accountService.reset()
    analyticsTrackingPreference.clear()
    appConfigService.reset()
    analyticsEventQueue.clear()
  }

  context("event queueing") {
    val accounts = listOf(FullAccountMock, LiteAccountMock)
    accounts.forEach { account ->
      test("queues event with active account - ${account::class.simpleName}") {
        accountService.setActiveAccount(account)
        eventTracker.track(ACTION_APP_ACCOUNT_CREATED)
        appScope.runCurrent()

        analyticsEventQueue.queueContents shouldHaveSize 1
        val queuedEvent = analyticsEventQueue.queueContents.first()

        queuedEvent.event.action.shouldBe(ACTION_APP_ACCOUNT_CREATED)
        queuedEvent.event.session_id.shouldBe(sessionId)
        queuedEvent.event.account_id.shouldBe(account.accountId.serverId)
        queuedEvent.event.country.shouldBe(countryCodeProvider.countryCode())
        queuedEvent.event.platform_info.shouldBe(platformInfoProvider.getPlatformInfo())
        queuedEvent.event.hw_info.shouldBe(hardWareInfoProvider.getHardwareInfo())
        queuedEvent.event.app_installation_id.shouldBe(
          appInstallationDao.appInstallation!!.localId
        )
        queuedEvent.event.app_device_id.shouldBe(platformInfoProvider.getPlatformInfo().device_id)
        if (account is FullAccount) {
          queuedEvent.event.keyset_id.shouldBe(SpendingKeysetMock.localId)
        } else {
          queuedEvent.event.keyset_id.shouldBeEmpty()
        }
      }
    }

    test("queues event with no active account") {
      accountService.clear()
      eventTracker.track(ACTION_APP_ACCOUNT_CREATED)
      appScope.runCurrent()

      analyticsEventQueue.queueContents shouldHaveSize 1
      val queuedEvent = analyticsEventQueue.queueContents.first()

      queuedEvent.event.action.shouldBe(ACTION_APP_ACCOUNT_CREATED)
      queuedEvent.event.session_id.shouldBe(sessionId)
      queuedEvent.event.account_id.shouldBeEmpty()
      queuedEvent.event.country.shouldBe(countryCodeProvider.countryCode())
      queuedEvent.event.platform_info.shouldBe(platformInfoProvider.getPlatformInfo())
      queuedEvent.event.hw_info.shouldBe(hardWareInfoProvider.getHardwareInfo())
      queuedEvent.event.app_installation_id.shouldBe(
        appInstallationDao.appInstallation!!.localId
      )
      queuedEvent.event.app_device_id.shouldBe(platformInfoProvider.getPlatformInfo().device_id)
      queuedEvent.event.keyset_id.shouldBeEmpty()
    }

    accounts.forEach { account ->
      test("queues event with onboarding account - ${account::class.simpleName}") {
        accountService.saveAccountAndBeginOnboarding(account)
        eventTracker.track(ACTION_APP_ACCOUNT_CREATED)
        appScope.runCurrent()

        analyticsEventQueue.queueContents shouldHaveSize 1
        val queuedEvent = analyticsEventQueue.queueContents.first()

        queuedEvent.event.action.shouldBe(ACTION_APP_ACCOUNT_CREATED)
        queuedEvent.event.session_id.shouldBe(sessionId)
        queuedEvent.event.account_id.shouldBe(account.accountId.serverId)
        queuedEvent.event.country.shouldBe(countryCodeProvider.countryCode())
        queuedEvent.event.platform_info.shouldBe(platformInfoProvider.getPlatformInfo())
        queuedEvent.event.hw_info.shouldBe(hardWareInfoProvider.getHardwareInfo())
        queuedEvent.event.app_installation_id.shouldBe(
          appInstallationDao.appInstallation!!.localId
        )
        queuedEvent.event.app_device_id.shouldBe(platformInfoProvider.getPlatformInfo().device_id)
        if (account is FullAccount) {
          queuedEvent.event.keyset_id.shouldBe(SpendingKeysetMock.localId)
        } else {
          queuedEvent.event.keyset_id.shouldBeEmpty()
        }
      }
    }

    test("queues event for lite account upgrading to full account") {
      val fullAccount = FullAccountMock
      accountService.accountState.value = Ok(
        AccountStatus.LiteAccountUpgradingToFullAccount(
          liteAccount = LiteAccountMock,
          onboardingAccount = fullAccount
        )
      )

      accountService.saveAccountAndBeginOnboarding(fullAccount)
      eventTracker.track(ACTION_APP_ACCOUNT_CREATED)
      appScope.runCurrent()

      analyticsEventQueue.queueContents shouldHaveSize 1
      val queuedEvent = analyticsEventQueue.queueContents.first()

      queuedEvent.event.action.shouldBe(ACTION_APP_ACCOUNT_CREATED)
      queuedEvent.event.session_id.shouldBe(sessionId)
      queuedEvent.event.account_id.shouldBe(fullAccount.accountId.serverId)
      queuedEvent.event.country.shouldBe(countryCodeProvider.countryCode())
      queuedEvent.event.platform_info.shouldBe(platformInfoProvider.getPlatformInfo())
      queuedEvent.event.hw_info.shouldBe(hardWareInfoProvider.getHardwareInfo())
      queuedEvent.event.app_installation_id.shouldBe(
        appInstallationDao.appInstallation!!.localId
      )
      queuedEvent.event.app_device_id.shouldBe(platformInfoProvider.getPlatformInfo().device_id)
      queuedEvent.event.keyset_id.shouldBe(SpendingKeysetMock.localId)
    }
  }

  test("Tracks screen events as expected") {
    eventTracker.track(
      EventTrackerScreenInfo(
        eventTrackerScreenId = PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS,
        eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
      )
    )
    appScope.runCurrent()

    analyticsEventQueue.queueContents shouldHaveSize 1
    val queuedEvent = analyticsEventQueue.queueContents.first()

    queuedEvent.event.action.shouldBe(ACTION_APP_SCREEN_IMPRESSION)
    queuedEvent.event.screen_id.shouldBe("HW_PAIR_INSTRUCTIONS_ACCOUNT_CREATION")
  }

  test("App device ID - Android platform") {
    deviceInfoProvider.devicePlatformValue = Android
    eventTracker.track(
      EventTrackerScreenInfo(
        eventTrackerScreenId = PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS,
        eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
      )
    )
    appScope.runCurrent()

    analyticsEventQueue.queueContents shouldHaveSize 1
    val queuedEvent = analyticsEventQueue.queueContents.first()
    queuedEvent.event.app_device_id.shouldBe(platformInfoProvider.getPlatformInfo().device_id)
  }

  test("App device ID - iOS platform") {
    deviceInfoProvider.devicePlatformValue = IOS
    eventTracker.track(
      EventTrackerScreenInfo(
        eventTrackerScreenId = PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS,
        eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
      )
    )
    appScope.runCurrent()

    analyticsEventQueue.queueContents shouldHaveSize 1
    val queuedEvent = analyticsEventQueue.queueContents.first()
    queuedEvent.event.app_device_id.shouldBe(appDeviceIdDao.appDeviceId)
  }

  test("No events are queued when tracking is disabled") {
    analyticsTrackingPreference.set(false)
    eventTracker.track(ACTION_APP_ACCOUNT_CREATED)
    appScope.runCurrent()

    // Event should still be stored locally but not queued for processing
    analyticsEventQueue.queueContents shouldHaveSize 0
  }
})
