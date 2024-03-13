@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.analytics.events

import build.wallet.account.AccountRepositoryFake
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
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.keybox.config.TemplateFullAccountConfigDaoFake
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryMock
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.device.DevicePlatform.Android
import build.wallet.platform.device.DevicePlatform.IOS
import build.wallet.platform.settings.LocaleCountryCodeProviderMock
import build.wallet.platform.settings.LocaleCurrencyCodeProviderMock
import build.wallet.queueprocessor.ProcessorMock
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

class EventTrackerImplTests : FunSpec({
  val eventProcessor = ProcessorMock<QueueAnalyticsEvent>(turbines::create)

  val sessionId = "session-id"
  val clock = ClockFake()
  val countryCodeProvider = LocaleCountryCodeProviderMock()
  val appInstallationDao = AppInstallationDaoMock()
  val hardWareInfoProvider = HardwareInfoProviderMock()
  val platformInfoProvider = PlatformInfoProviderMock()
  val appDeviceIdDao = AppDeviceIdDaoMock()
  val deviceInfoProvider = DeviceInfoProviderMock()
  val accountRepository = AccountRepositoryFake()
  val eventStore = EventStoreMock()
  val featureFlagDao = FeatureFlagDaoMock()
  val analyticsTrackingEnabledFeatureFlag =
    AnalyticsTrackingEnabledFeatureFlag(
      appVariant = Customer,
      featureFlagDao = featureFlagDao
    )
  val templateFullAccountConfigDao = TemplateFullAccountConfigDaoFake()

  val appScope = TestScope()

  val eventTracker =
    EventTrackerImpl(
      appCoroutineScope = appScope,
      appDeviceIdDao = appDeviceIdDao,
      deviceInfoProvider = deviceInfoProvider,
      accountRepository = accountRepository,
      templateFullAccountConfigDao = templateFullAccountConfigDao,
      clock = clock,
      countryCodeProvider = countryCodeProvider,
      eventProcessor = eventProcessor,
      appInstallationDao = appInstallationDao,
      hardwareInfoProvider = hardWareInfoProvider,
      platformInfoProvider = platformInfoProvider,
      sessionIdProvider = SessionIdProviderMock(sessionId),
      eventStore = eventStore,
      analyticsTrackingEnabledFeatureFlag = analyticsTrackingEnabledFeatureFlag,
      bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryMock(),
      fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create),
      localeCurrencyCodeProvider = LocaleCurrencyCodeProviderMock()
    )

  beforeTest {
    eventProcessor.processBatchReturnValues = listOf(Ok(Unit))
  }

  afterTest {
    accountRepository.reset()
    eventProcessor.reset()
  }

  context("happy path") {
    val accounts = listOf(FullAccountMock, LiteAccountMock)
    accounts.forEach { account ->
      test("has active account - ${account::class.simpleName}") {
        accountRepository.setActiveAccount(account)
        eventTracker.track(ACTION_APP_ACCOUNT_CREATED)
        appScope.runCurrent()
        val persistedEvent =
          (eventProcessor.processBatchCalls.awaitItem() as List<QueueAnalyticsEvent>).first()

        persistedEvent.event.action.shouldBe(ACTION_APP_ACCOUNT_CREATED)
        persistedEvent.event.session_id.shouldBe(sessionId)
        persistedEvent.event.account_id.shouldBe(account.accountId.serverId)
        persistedEvent.event.country.shouldBe(countryCodeProvider.countryCode())
        persistedEvent.event.platform_info.shouldBe(platformInfoProvider.getPlatformInfo())
        persistedEvent.event.hw_info.shouldBe(hardWareInfoProvider.getHardwareInfo())
        persistedEvent.event.app_installation_id.shouldBe(
          appInstallationDao.appInstallation!!.localId
        )
        persistedEvent.event.app_device_id.shouldBe(platformInfoProvider.getPlatformInfo().device_id)
        if (account is FullAccount) {
          persistedEvent.event.keyset_id.shouldBe(SpendingKeysetMock.localId)
        } else {
          persistedEvent.event.keyset_id.shouldBeEmpty()
        }
      }
    }

    test("no active account") {
      accountRepository.clear()
      eventTracker.track(ACTION_APP_ACCOUNT_CREATED)
      appScope.runCurrent()
      val persistedEvent =
        (eventProcessor.processBatchCalls.awaitItem() as List<QueueAnalyticsEvent>).first()

      persistedEvent.event.action.shouldBe(ACTION_APP_ACCOUNT_CREATED)
      persistedEvent.event.session_id.shouldBe(sessionId)
      persistedEvent.event.account_id.shouldBeEmpty()
      persistedEvent.event.country.shouldBe(countryCodeProvider.countryCode())
      persistedEvent.event.platform_info.shouldBe(platformInfoProvider.getPlatformInfo())
      persistedEvent.event.hw_info.shouldBe(hardWareInfoProvider.getHardwareInfo())
      persistedEvent.event.app_installation_id.shouldBe(
        appInstallationDao.appInstallation!!.localId
      )
      persistedEvent.event.app_device_id.shouldBe(platformInfoProvider.getPlatformInfo().device_id)
      persistedEvent.event.keyset_id.shouldBeEmpty()
    }

    accounts.forEach { account ->
      test("onboarding account - ${account::class.simpleName}") {
        accountRepository.saveAccountAndBeginOnboarding(account)
        eventTracker.track(ACTION_APP_ACCOUNT_CREATED)
        appScope.runCurrent()
        val persistedEvent =
          (eventProcessor.processBatchCalls.awaitItem() as List<QueueAnalyticsEvent>).first()

        persistedEvent.event.action.shouldBe(ACTION_APP_ACCOUNT_CREATED)
        persistedEvent.event.session_id.shouldBe(sessionId)
        persistedEvent.event.account_id.shouldBe(account.accountId.serverId)
        persistedEvent.event.country.shouldBe(countryCodeProvider.countryCode())
        persistedEvent.event.platform_info.shouldBe(platformInfoProvider.getPlatformInfo())
        persistedEvent.event.hw_info.shouldBe(hardWareInfoProvider.getHardwareInfo())
        persistedEvent.event.app_installation_id.shouldBe(
          appInstallationDao.appInstallation!!.localId
        )
        persistedEvent.event.app_device_id.shouldBe(platformInfoProvider.getPlatformInfo().device_id)
        if (account is FullAccount) {
          persistedEvent.event.keyset_id.shouldBe(SpendingKeysetMock.localId)
        } else {
          persistedEvent.event.keyset_id.shouldBeEmpty()
        }
      }
    }

    test("lite account upgrading to full account") {
      val account = FullAccountMock
      accountRepository.accountState.value = Ok(
        AccountStatus.LiteAccountUpgradingToFullAccount(account)
      )

      accountRepository.saveAccountAndBeginOnboarding(account)
      eventTracker.track(ACTION_APP_ACCOUNT_CREATED)
      appScope.runCurrent()
      val persistedEvent =
        (eventProcessor.processBatchCalls.awaitItem() as List<QueueAnalyticsEvent>).first()

      persistedEvent.event.action.shouldBe(ACTION_APP_ACCOUNT_CREATED)
      persistedEvent.event.session_id.shouldBe(sessionId)
      persistedEvent.event.account_id.shouldBe(account.accountId.serverId)
      persistedEvent.event.country.shouldBe(countryCodeProvider.countryCode())
      persistedEvent.event.platform_info.shouldBe(platformInfoProvider.getPlatformInfo())
      persistedEvent.event.hw_info.shouldBe(hardWareInfoProvider.getHardwareInfo())
      persistedEvent.event.app_installation_id.shouldBe(
        appInstallationDao.appInstallation!!.localId
      )
      persistedEvent.event.app_device_id.shouldBe(platformInfoProvider.getPlatformInfo().device_id)
      persistedEvent.event.keyset_id.shouldBe(SpendingKeysetMock.localId)
    }
  }

  test("Tracks screen events as expected") {
    eventTracker.track(
      EventTrackerScreenInfo(
        eventTrackerScreenId = PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS,
        eventTrackerScreenIdContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
      )
    )
    appScope.runCurrent()

    val persistedEvent =
      (eventProcessor.processBatchCalls.awaitItem() as List<QueueAnalyticsEvent>).first()

    persistedEvent.event.action.shouldBe(ACTION_APP_SCREEN_IMPRESSION)
    persistedEvent.event.screen_id.shouldBe("HW_PAIR_INSTRUCTIONS_ACCOUNT_CREATION")
  }

  test("App device ID - Android platform") {
    deviceInfoProvider.devicePlatformValue = Android
    eventTracker.track(
      EventTrackerScreenInfo(
        eventTrackerScreenId = PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS,
        eventTrackerScreenIdContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
      )
    )
    appScope.runCurrent()

    val persistedEvent =
      (eventProcessor.processBatchCalls.awaitItem() as List<QueueAnalyticsEvent>).first()
    persistedEvent.event.app_device_id.shouldBe(platformInfoProvider.getPlatformInfo().device_id)
  }

  test("App device ID - iOS platform") {
    deviceInfoProvider.devicePlatformValue = IOS
    eventTracker.track(
      EventTrackerScreenInfo(
        eventTrackerScreenId = PairHardwareEventTrackerScreenId.HW_PAIR_INSTRUCTIONS,
        eventTrackerScreenIdContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
      )
    )
    appScope.runCurrent()

    val persistedEvent =
      (eventProcessor.processBatchCalls.awaitItem() as List<QueueAnalyticsEvent>).first()
    persistedEvent.event.app_device_id.shouldBe(appDeviceIdDao.appDeviceId)
  }
})
