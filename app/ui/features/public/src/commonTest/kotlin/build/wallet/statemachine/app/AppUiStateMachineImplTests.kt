package build.wallet.statemachine.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import bitkey.datadog.DatadogRumMonitorFake
import bitkey.ui.framework.NavigatorPresenterFake
import build.wallet.account.AccountServiceFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.id.FwupEventTrackerScreenId
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.LOADING_APP
import build.wallet.analytics.v1.Action.ACTION_APP_SCREEN_IMPRESSION
import build.wallet.bitkey.keybox.AppKeyBundleMock2
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keybox.SoftwareAccountMock
import build.wallet.bootstrap.AppState
import build.wallet.bootstrap.AppState.HasActiveSoftwareAccount
import build.wallet.bootstrap.LoadAppServiceFake
import build.wallet.cloud.backup.AllLiteAccountBackupMocks
import build.wallet.cloud.backup.CloudBackup
import build.wallet.coroutines.turbine.awaitItemMaybe
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.feature.flags.AppUpdateModalFeatureFlag
import build.wallet.platform.config.AppVariant
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.device.DevicePlatform
import build.wallet.platform.links.AppStoreUrlProviderMock
import build.wallet.platform.links.DeepLinkHandlerMock
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.CreateAccountUiProps
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachine
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiProps
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiStateMachine
import build.wallet.statemachine.account.full.FullAccountUiProps
import build.wallet.statemachine.account.full.FullAccountUiStateMachine
import build.wallet.statemachine.core.AppUpdateModalBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SplashBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.fwup.FwupNextComponentReadyModel
import build.wallet.statemachine.fwup.FwupNfcBodyModel
import build.wallet.statemachine.home.full.HomeUiProps
import build.wallet.statemachine.home.full.HomeUiStateMachine
import build.wallet.statemachine.home.lite.LiteHomeUiProps
import build.wallet.statemachine.home.lite.LiteHomeUiStateMachine
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcBodyModel
import build.wallet.statemachine.recovery.cloud.LiteAccountCloudBackupRestorationUiProps
import build.wallet.statemachine.recovery.cloud.LiteAccountCloudBackupRestorationUiStateMachine
import build.wallet.statemachine.root.*
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilBodyMock
import build.wallet.worker.AppWorkerExecutorMock
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.logging.LogLevel
import build.wallet.logging.LogWriterMock
import build.wallet.logging.Logger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.DelicateCoroutinesApi
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import kotlin.time.Duration.Companion.milliseconds

class AppUiStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)
  val navigatorPresenter = NavigatorPresenterFake()

  val createAccountUiStateMachine =
    object : CreateAccountUiStateMachine,
      ScreenStateMachineMock<CreateAccountUiProps>(id = "create-account") {}
  val createLiteAccountUiStateMachine =
    object : CreateLiteAccountUiStateMachine,
      ScreenStateMachineMock<CreateLiteAccountUiProps>(id = "create-lite-account") {}
  val fullAccountUiStateMachine =
    object : FullAccountUiStateMachine,
      ScreenStateMachineMock<FullAccountUiProps>(id = "full-account") {}
  val noActiveAccountUiStateMachine =
    object : NoActiveAccountUiStateMachine,
      ScreenStateMachineMock<NoActiveAccountUiProps>(id = "no-active-account") {}
  val loadAppService = LoadAppServiceFake()
  val liteAccountCloudBackupRestorationUiStateMachine =
    object : LiteAccountCloudBackupRestorationUiStateMachine,
      ScreenStateMachineMock<LiteAccountCloudBackupRestorationUiProps>(
        id = "recover-lite-account"
      ) {}

  lateinit var stateMachine: AppUiStateMachineImpl

  val appWorkerExecutor = AppWorkerExecutorMock(turbines::create)

  val datadogRumMonitor = DatadogRumMonitorFake(turbines::create)

  val deepLinkHandler = DeepLinkHandlerMock(turbines::create)

  val appStoreUrlProvider = AppStoreUrlProviderMock()

  val appUpdateModalFeatureFlag = AppUpdateModalFeatureFlag(FeatureFlagDaoFake())

  val logWriter = LogWriterMock()

  // Fakes are stateful, need to reinitialize before each test to reset the state.
  beforeTest {
    logWriter.clear()
    Logger.configure(
      tag = "Test",
      minimumLogLevel = LogLevel.Verbose,
      logWriters = listOf(logWriter)
    )
    loadAppService.reset()
    loadAppService.appState.value = AppState.NoActiveAccount
    appUpdateModalFeatureFlag.setFlagValue(BooleanFlag(false))
    deepLinkHandler.reset()
    appStoreUrlProvider.reset()
    stateMachine =
      AppUiStateMachineImpl(
        appVariant = AppVariant.Development,
        navigatorPresenter = navigatorPresenter,
        eventTracker = eventTracker,
        homeUiStateMachine = object : HomeUiStateMachine,
          ScreenStateMachineMock<HomeUiProps>(id = "home") {},
        liteHomeUiStateMachine = object : LiteHomeUiStateMachine,
          ScreenStateMachineMock<LiteHomeUiProps>(id = "lite-home") {},
        fullAccountUiStateMachine = fullAccountUiStateMachine,
        createAccountUiStateMachine = createAccountUiStateMachine,
        noActiveAccountUiStateMachine = noActiveAccountUiStateMachine,
        loadAppService = loadAppService,
        createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
        liteAccountCloudBackupRestorationUiStateMachine =
        liteAccountCloudBackupRestorationUiStateMachine,
        appWorkerExecutor = appWorkerExecutor,
        accountService = AccountServiceFake(),
        datadogRumMonitor = datadogRumMonitor,
        splashScreenDelay = SplashScreenDelay(10.milliseconds),
        welcomeToBitkeyScreenDuration = WelcomeToBitkeyScreenDuration(10.milliseconds),
        deviceInfoProvider = DeviceInfoProviderMock(),
        appUpdateModalFeatureFlag = appUpdateModalFeatureFlag,
        appStoreUrlProvider = appStoreUrlProvider,
        deepLinkHandler = deepLinkHandler
      )
  }

  suspend fun EventTrackerMock.awaitSplashScreenEvent() {
    eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_SCREEN_IMPRESSION, GeneralEventTrackerScreenId.SPLASH_SCREEN)
    )
  }

  test("Loading until LoadAppService returns the state") {
    loadAppService.appState.value = null

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = AppState.HasActiveFullAccount(
        account = FullAccountMock,
        pendingAuthKeyRotation = null
      )

      awaitBodyMock<FullAccountUiProps> {
        account.shouldBe(FullAccountMock)
      }

      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("NoActiveAccount shows NoActiveAccountUiStateMachine") {
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitBodyMock<NoActiveAccountUiProps>()

      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("existing software account") {
    loadAppService.appState.value = null

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = HasActiveSoftwareAccount(
        account = SoftwareAccountMock
      )

      awaitBodyMock<HomeUiProps> {
        account.shouldBe(SoftwareAccountMock)
      }
      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("create a new software account") {
    loadAppService.appState.value = null

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = AppState.NoActiveAccount

      awaitBodyMock<NoActiveAccountUiProps> {
        onSoftwareWalletCreated(SoftwareAccountMock)
      }

      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Welcome to Bitkey")
      }

      awaitBodyMock<HomeUiProps> {
        account.shouldBe(SoftwareAccountMock)
      }
      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Launching from an onboarding account") {
    loadAppService.appState.value = AppState.OnboardingFullAccount(
      account = FullAccountMock
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      awaitBodyMock<CreateAccountUiProps> {
        onOnboardingComplete(FullAccountMock)
      }

      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Welcome to Bitkey")
      }

      awaitBodyMock<FullAccountUiProps> {
        account.shouldBe(FullAccountMock)
        isNewlyCreatedAccount.shouldBe(true)
      }
      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("creating a lite account") {
    loadAppService.appState.value = null

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = AppState.NoActiveAccount

      awaitBodyMock<NoActiveAccountUiProps> {
        onStartLiteAccountCreation("invite-code", StartIntent.BeTrustedContact)
      }

      awaitBodyMock<CreateLiteAccountUiProps> {
        onAccountCreated(LiteAccountMock)
      }

      awaitBodyMock<LiteHomeUiProps>()
      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("recovering a lite account") {
    loadAppService.appState.value = null

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = AppState.NoActiveAccount

      awaitBodyMock<NoActiveAccountUiProps> {
        onStartLiteAccountRecovery(AllLiteAccountBackupMocks[0] as CloudBackup)
      }

      awaitBodyMock<LiteAccountCloudBackupRestorationUiProps> {
        onLiteAccountRestored(LiteAccountMock)
      }

      awaitBodyMock<LiteHomeUiProps>()
      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("creating a full account") {
    loadAppService.appState.value = null

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = AppState.NoActiveAccount

      awaitBodyMock<NoActiveAccountUiProps> {
        onCreateFullAccount()
      }

      awaitBodyMock<CreateAccountUiProps> {
        onOnboardingComplete(FullAccountMock)
      }

      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Welcome to Bitkey")
      }

      awaitBodyMock<FullAccountUiProps> {
        account.shouldBe(FullAccountMock)
      }
      appWorkerExecutor.executeAllCalls.awaitItem()

      @OptIn(DelicateCoroutinesApi::class)
      eventTracker.eventCalls.awaitItemMaybe()
        ?.shouldBe(TrackedAction(ACTION_APP_SCREEN_IMPRESSION, LOADING_APP))

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Update modal shows when feature flag is enabled") {
    appUpdateModalFeatureFlag.setFlagValue(BooleanFlag(true))

    loadAppService.appState.value = AppState.HasActiveFullAccount(
      account = FullAccountMock,
      pendingAuthKeyRotation = null
    )

    stateMachine.test(Unit) {
      awaitBody<AppUpdateModalBodyModel> {
        onUpdate()
        deepLinkHandler.openDeeplinkCalls.awaitItem()
          .shouldBe("https://fake.app.store/test")
      }
      eventTracker.awaitSplashScreenEvent()

      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Update modal cancel dismisses modal") {
    appUpdateModalFeatureFlag.setFlagValue(BooleanFlag(true))

    loadAppService.appState.value = AppState.HasActiveFullAccount(
      account = FullAccountMock,
      pendingAuthKeyRotation = null
    )

    stateMachine.test(Unit) {
      awaitBody<AppUpdateModalBodyModel> {
        onCancel()
      }

      awaitUntilBodyMock<FullAccountUiProps> {
        account.shouldBe(FullAccountMock)
      }

      eventTracker.awaitSplashScreenEvent()
      appWorkerExecutor.executeAllCalls.awaitItem()

      @OptIn(DelicateCoroutinesApi::class)
      eventTracker.eventCalls.awaitItemMaybe()
        ?.shouldBe(TrackedAction(ACTION_APP_SCREEN_IMPRESSION, LOADING_APP))

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Update modal does not show when feature flag is disabled") {
    appUpdateModalFeatureFlag.setFlagValue(BooleanFlag(false))

    loadAppService.appState.value = AppState.HasActiveFullAccount(
      account = FullAccountMock,
      pendingAuthKeyRotation = null
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      awaitBodyMock<FullAccountUiProps> {
        account.shouldBe(FullAccountMock)
      }

      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("NoActiveAccountUiStateMachine transitions to ViewingFullAccount via onViewFullAccount") {
    val accountService = AccountServiceFake()
    loadAppService.appState.value = AppState.NoActiveAccount

    // Reinitialize stateMachine with the accountService we can control
    stateMachine = AppUiStateMachineImpl(
      appVariant = AppVariant.Development,
      navigatorPresenter = navigatorPresenter,
      eventTracker = eventTracker,
      homeUiStateMachine = object : HomeUiStateMachine,
        ScreenStateMachineMock<HomeUiProps>(id = "home") {},
      liteHomeUiStateMachine = object : LiteHomeUiStateMachine,
        ScreenStateMachineMock<LiteHomeUiProps>(id = "lite-home") {},
      fullAccountUiStateMachine = fullAccountUiStateMachine,
      createAccountUiStateMachine = createAccountUiStateMachine,
      noActiveAccountUiStateMachine = noActiveAccountUiStateMachine,
      loadAppService = loadAppService,
      createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
      liteAccountCloudBackupRestorationUiStateMachine = liteAccountCloudBackupRestorationUiStateMachine,
      appWorkerExecutor = appWorkerExecutor,
      accountService = accountService,
      datadogRumMonitor = datadogRumMonitor,
      splashScreenDelay = SplashScreenDelay(10.milliseconds),
      welcomeToBitkeyScreenDuration = WelcomeToBitkeyScreenDuration(10.milliseconds),
      deviceInfoProvider = DeviceInfoProviderMock(),
      appUpdateModalFeatureFlag = appUpdateModalFeatureFlag,
      appStoreUrlProvider = appStoreUrlProvider,
      deepLinkHandler = deepLinkHandler
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      // Should show NoActiveAccountUiProps initially
      awaitBodyMock<NoActiveAccountUiProps> {
        // Simulate the UI state machine calling onViewFullAccount
        onViewFullAccount(FullAccountMock)
      }

      // Should transition to FullAccountUiProps
      awaitBodyMock<FullAccountUiProps> {
        account.shouldBe(FullAccountMock)
      }

      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("AppLoadedDataScreenModel transitions to NoActiveAccount when account is cleared") {
    val accountService = AccountServiceFake()
    loadAppService.appState.value = AppState.HasActiveFullAccount(
      account = FullAccountMock,
      pendingAuthKeyRotation = null
    )
    // Reinitialize stateMachine with the accountService we can control
    stateMachine = AppUiStateMachineImpl(
      appVariant = AppVariant.Development,
      navigatorPresenter = navigatorPresenter,
      eventTracker = eventTracker,
      homeUiStateMachine = object : HomeUiStateMachine,
        ScreenStateMachineMock<HomeUiProps>(id = "home") {},
      liteHomeUiStateMachine = object : LiteHomeUiStateMachine,
        ScreenStateMachineMock<LiteHomeUiProps>(id = "lite-home") {},
      fullAccountUiStateMachine = fullAccountUiStateMachine,
      createAccountUiStateMachine = createAccountUiStateMachine,
      noActiveAccountUiStateMachine = noActiveAccountUiStateMachine,
      loadAppService = loadAppService,
      createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
      liteAccountCloudBackupRestorationUiStateMachine = liteAccountCloudBackupRestorationUiStateMachine,
      appWorkerExecutor = appWorkerExecutor,
      accountService = accountService,
      datadogRumMonitor = datadogRumMonitor,
      splashScreenDelay = SplashScreenDelay(10.milliseconds),
      welcomeToBitkeyScreenDuration = WelcomeToBitkeyScreenDuration(10.milliseconds),
      deviceInfoProvider = DeviceInfoProviderMock(),
      appUpdateModalFeatureFlag = appUpdateModalFeatureFlag,
      appStoreUrlProvider = appStoreUrlProvider,
      deepLinkHandler = deepLinkHandler
    )

    // Set initial account
    accountService.setActiveAccount(FullAccountMock)

    stateMachine.test(Unit) {
      awaitUntilBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      // Should show FullAccountUiProps initially
      awaitUntilBodyMock<FullAccountUiProps> {
        account.shouldBe(FullAccountMock)
      }

      // Simulate account being cleared
      accountService.clear()

      // Should transition to NoActiveAccountUiProps
      awaitUntilBodyMock<NoActiveAccountUiProps> {}

      appWorkerExecutor.executeAllCalls.awaitItem()
      eventTracker.eventCalls.cancelAndIgnoreRemainingEvents()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("AppLoadedDataScreenModel refreshes FullAccount props when active account changes") {
    val accountService = AccountServiceFake()
    val localEventTracker = EventTrackerMock { name ->
      turbines.create("$name refresh full account props")
    }
    val rotatedAccount = FullAccountMock.copy(
      keybox = FullAccountMock.keybox.copy(
        activeAppKeyBundle = AppKeyBundleMock2
      )
    )
    loadAppService.appState.value = AppState.HasActiveFullAccount(
      account = FullAccountMock,
      pendingAuthKeyRotation = null
    )

    stateMachine = AppUiStateMachineImpl(
      appVariant = AppVariant.Development,
      navigatorPresenter = navigatorPresenter,
      eventTracker = localEventTracker,
      homeUiStateMachine = object : HomeUiStateMachine,
        ScreenStateMachineMock<HomeUiProps>(id = "home") {},
      liteHomeUiStateMachine = object : LiteHomeUiStateMachine,
        ScreenStateMachineMock<LiteHomeUiProps>(id = "lite-home") {},
      fullAccountUiStateMachine = fullAccountUiStateMachine,
      createAccountUiStateMachine = createAccountUiStateMachine,
      noActiveAccountUiStateMachine = noActiveAccountUiStateMachine,
      loadAppService = loadAppService,
      createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
      liteAccountCloudBackupRestorationUiStateMachine = liteAccountCloudBackupRestorationUiStateMachine,
      appWorkerExecutor = appWorkerExecutor,
      accountService = accountService,
      datadogRumMonitor = datadogRumMonitor,
      splashScreenDelay = SplashScreenDelay(10.milliseconds),
      welcomeToBitkeyScreenDuration = WelcomeToBitkeyScreenDuration(10.milliseconds),
      deviceInfoProvider = DeviceInfoProviderMock(),
      appUpdateModalFeatureFlag = appUpdateModalFeatureFlag,
      appStoreUrlProvider = appStoreUrlProvider,
      deepLinkHandler = deepLinkHandler
    )

    accountService.setActiveAccount(FullAccountMock)

    stateMachine.test(Unit) {
      awaitUntilBody<SplashBodyModel>()
      localEventTracker.awaitSplashScreenEvent()

      awaitUntilBodyMock<FullAccountUiProps> {
        account.shouldBe(FullAccountMock)
      }

      accountService.setActiveAccount(rotatedAccount)

      awaitUntilBodyMock<FullAccountUiProps> {
        account.shouldBe(rotatedAccount)
      }

      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("DSV2 iOS keeps previous screen for generic platform NFC models using the native sheet") {

    val deviceInfoProvider = DeviceInfoProviderMock().apply {
      devicePlatformValue = DevicePlatform.IOS
    }
    val localEventTracker = EventTrackerMock { name ->
      turbines.create("$name dsv2 ios platform nfc")
    }
    val homeUiStateMachine = ToggleablePlatformNfcHomeUiStateMachine()
    loadAppService.appState.value = HasActiveSoftwareAccount(
      account = SoftwareAccountMock
    )

    stateMachine = AppUiStateMachineImpl(
      appVariant = AppVariant.Development,
      navigatorPresenter = navigatorPresenter,
      eventTracker = localEventTracker,
      homeUiStateMachine = homeUiStateMachine,
      liteHomeUiStateMachine = object : LiteHomeUiStateMachine,
        ScreenStateMachineMock<LiteHomeUiProps>(id = "lite-home") {},
      fullAccountUiStateMachine = fullAccountUiStateMachine,
      createAccountUiStateMachine = createAccountUiStateMachine,
      noActiveAccountUiStateMachine = noActiveAccountUiStateMachine,
      loadAppService = loadAppService,
      createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
      liteAccountCloudBackupRestorationUiStateMachine = liteAccountCloudBackupRestorationUiStateMachine,
      appWorkerExecutor = appWorkerExecutor,
      accountService = AccountServiceFake(),
      datadogRumMonitor = datadogRumMonitor,
      splashScreenDelay = SplashScreenDelay(10.milliseconds),
      welcomeToBitkeyScreenDuration = WelcomeToBitkeyScreenDuration(10.milliseconds),
      deviceInfoProvider = deviceInfoProvider,
      appUpdateModalFeatureFlag = appUpdateModalFeatureFlag,
      appStoreUrlProvider = appStoreUrlProvider,
      deepLinkHandler = deepLinkHandler
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      localEventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_SCREEN_IMPRESSION, GeneralEventTrackerScreenId.SPLASH_SCREEN)
      )

      awaitBodyMock<HomeUiProps> {
        account.shouldBe(SoftwareAccountMock)
      }

      homeUiStateMachine.shouldShowPlatformNfcScreen = true

      awaitItemMaybe(timeout = 100.milliseconds).shouldBe(null)

      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("DSV2 iOS replaces the current screen for generic platform NFC models using a custom background") {

    val deviceInfoProvider = DeviceInfoProviderMock().apply {
      devicePlatformValue = DevicePlatform.IOS
    }
    val localEventTracker = EventTrackerMock { name ->
      turbines.create("$name dsv2 ios platform nfc custom background")
    }
    val homeUiStateMachine = ToggleablePlatformNfcHomeUiStateMachine(showNativeSheetOnIos = false)
    loadAppService.appState.value = HasActiveSoftwareAccount(
      account = SoftwareAccountMock
    )

    stateMachine = AppUiStateMachineImpl(
      appVariant = AppVariant.Development,
      navigatorPresenter = navigatorPresenter,
      eventTracker = localEventTracker,
      homeUiStateMachine = homeUiStateMachine,
      liteHomeUiStateMachine = object : LiteHomeUiStateMachine,
        ScreenStateMachineMock<LiteHomeUiProps>(id = "lite-home") {},
      fullAccountUiStateMachine = fullAccountUiStateMachine,
      createAccountUiStateMachine = createAccountUiStateMachine,
      noActiveAccountUiStateMachine = noActiveAccountUiStateMachine,
      loadAppService = loadAppService,
      createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
      liteAccountCloudBackupRestorationUiStateMachine = liteAccountCloudBackupRestorationUiStateMachine,
      appWorkerExecutor = appWorkerExecutor,
      accountService = AccountServiceFake(),
      datadogRumMonitor = datadogRumMonitor,
      splashScreenDelay = SplashScreenDelay(10.milliseconds),
      welcomeToBitkeyScreenDuration = WelcomeToBitkeyScreenDuration(10.milliseconds),
      deviceInfoProvider = deviceInfoProvider,
      appUpdateModalFeatureFlag = appUpdateModalFeatureFlag,
      appStoreUrlProvider = appStoreUrlProvider,
      deepLinkHandler = deepLinkHandler
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      localEventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_SCREEN_IMPRESSION, GeneralEventTrackerScreenId.SPLASH_SCREEN)
      )

      awaitBodyMock<HomeUiProps> {
        account.shouldBe(SoftwareAccountMock)
      }

      homeUiStateMachine.shouldShowPlatformNfcScreen = true

      awaitUntilBody<NfcBodyModel> {
        text.shouldBe("Hold your Bitkey to the back of your phone")
      }

      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("DSV2 iOS replaces the current screen for FWUP platform NFC models") {

    val deviceInfoProvider = DeviceInfoProviderMock().apply {
      devicePlatformValue = DevicePlatform.IOS
    }
    val localEventTracker = EventTrackerMock { name ->
      turbines.create("$name dsv2 ios fwup platform nfc")
    }
    val homeUiStateMachine = ToggleablePlatformFwupHomeUiStateMachine()
    loadAppService.appState.value = HasActiveSoftwareAccount(
      account = SoftwareAccountMock
    )

    stateMachine = AppUiStateMachineImpl(
      appVariant = AppVariant.Development,
      navigatorPresenter = navigatorPresenter,
      eventTracker = localEventTracker,
      homeUiStateMachine = homeUiStateMachine,
      liteHomeUiStateMachine = object : LiteHomeUiStateMachine,
        ScreenStateMachineMock<LiteHomeUiProps>(id = "lite-home") {},
      fullAccountUiStateMachine = fullAccountUiStateMachine,
      createAccountUiStateMachine = createAccountUiStateMachine,
      noActiveAccountUiStateMachine = noActiveAccountUiStateMachine,
      loadAppService = loadAppService,
      createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
      liteAccountCloudBackupRestorationUiStateMachine = liteAccountCloudBackupRestorationUiStateMachine,
      appWorkerExecutor = appWorkerExecutor,
      accountService = AccountServiceFake(),
      datadogRumMonitor = datadogRumMonitor,
      splashScreenDelay = SplashScreenDelay(10.milliseconds),
      welcomeToBitkeyScreenDuration = WelcomeToBitkeyScreenDuration(10.milliseconds),
      deviceInfoProvider = deviceInfoProvider,
      appUpdateModalFeatureFlag = appUpdateModalFeatureFlag,
      appStoreUrlProvider = appStoreUrlProvider,
      deepLinkHandler = deepLinkHandler
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      localEventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_SCREEN_IMPRESSION, GeneralEventTrackerScreenId.SPLASH_SCREEN)
      )

      awaitBodyMock<HomeUiProps> {
        account.shouldBe(SoftwareAccountMock)
      }

      homeUiStateMachine.shouldShowPlatformNfcScreen = true

      awaitUntilBody<FwupNfcBodyModel> {
        status.shouldBe(FwupNfcBodyModel.Status.Searching())
      }

      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("DSV2 iOS shows FWUP success instead of resurfacing the previous continuation screen") {

    val deviceInfoProvider = DeviceInfoProviderMock().apply {
      devicePlatformValue = DevicePlatform.IOS
    }
    val localEventTracker = EventTrackerMock { name ->
      turbines.create("$name dsv2 ios fwup success")
    }
    val homeUiStateMachine = ToggleablePlatformFwupHomeUiStateMachine()
    loadAppService.appState.value = HasActiveSoftwareAccount(
      account = SoftwareAccountMock
    )

    stateMachine = AppUiStateMachineImpl(
      appVariant = AppVariant.Development,
      navigatorPresenter = navigatorPresenter,
      eventTracker = localEventTracker,
      homeUiStateMachine = homeUiStateMachine,
      liteHomeUiStateMachine = object : LiteHomeUiStateMachine,
        ScreenStateMachineMock<LiteHomeUiProps>(id = "lite-home") {},
      fullAccountUiStateMachine = fullAccountUiStateMachine,
      createAccountUiStateMachine = createAccountUiStateMachine,
      noActiveAccountUiStateMachine = noActiveAccountUiStateMachine,
      loadAppService = loadAppService,
      createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
      liteAccountCloudBackupRestorationUiStateMachine = liteAccountCloudBackupRestorationUiStateMachine,
      appWorkerExecutor = appWorkerExecutor,
      accountService = AccountServiceFake(),
      datadogRumMonitor = datadogRumMonitor,
      splashScreenDelay = SplashScreenDelay(10.milliseconds),
      welcomeToBitkeyScreenDuration = WelcomeToBitkeyScreenDuration(10.milliseconds),
      deviceInfoProvider = deviceInfoProvider,
      appUpdateModalFeatureFlag = appUpdateModalFeatureFlag,
      appStoreUrlProvider = appStoreUrlProvider,
      deepLinkHandler = deepLinkHandler
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      localEventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_SCREEN_IMPRESSION, GeneralEventTrackerScreenId.SPLASH_SCREEN)
      )

      awaitBodyMock<HomeUiProps> {
        account.shouldBe(SoftwareAccountMock)
      }

      homeUiStateMachine.bodyModel = FwupNextComponentReadyModel(
        completedIndex = 1,
        totalMcus = 2,
        onBack = {},
        onContinue = {}
      )

      awaitBody<FwupNextComponentReadyModel> {
        completedIndex.shouldBe(1)
        totalMcus.shouldBe(2)
      }
      localEventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_SCREEN_IMPRESSION, FwupEventTrackerScreenId.FWUP_NEXT_COMPONENT_READY)
      )

      homeUiStateMachine.bodyModel = FwupNfcBodyModel.Status.Success()

      awaitUntilBody<FwupNfcBodyModel> {
        status.shouldBe(FwupNfcBodyModel.Status.Success())
      }

      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("DSV2 iOS keeps previous screen for sign transaction models using the native sheet") {

    val deviceInfoProvider = DeviceInfoProviderMock().apply {
      devicePlatformValue = DevicePlatform.IOS
    }
    val localEventTracker = EventTrackerMock { name ->
      turbines.create("$name dsv2 ios sign transaction native sheet")
    }
    val homeUiStateMachine = ToggleableSignTransactionHomeUiStateMachine(showNativeSheetOnIos = true)
    loadAppService.appState.value = HasActiveSoftwareAccount(
      account = SoftwareAccountMock
    )

    stateMachine = AppUiStateMachineImpl(
      appVariant = AppVariant.Development,
      navigatorPresenter = navigatorPresenter,
      eventTracker = localEventTracker,
      homeUiStateMachine = homeUiStateMachine,
      liteHomeUiStateMachine = object : LiteHomeUiStateMachine,
        ScreenStateMachineMock<LiteHomeUiProps>(id = "lite-home") {},
      fullAccountUiStateMachine = fullAccountUiStateMachine,
      createAccountUiStateMachine = createAccountUiStateMachine,
      noActiveAccountUiStateMachine = noActiveAccountUiStateMachine,
      loadAppService = loadAppService,
      createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
      liteAccountCloudBackupRestorationUiStateMachine = liteAccountCloudBackupRestorationUiStateMachine,
      appWorkerExecutor = appWorkerExecutor,
      accountService = AccountServiceFake(),
      datadogRumMonitor = datadogRumMonitor,
      splashScreenDelay = SplashScreenDelay(10.milliseconds),
      welcomeToBitkeyScreenDuration = WelcomeToBitkeyScreenDuration(10.milliseconds),
      deviceInfoProvider = deviceInfoProvider,
      appUpdateModalFeatureFlag = appUpdateModalFeatureFlag,
      appStoreUrlProvider = appStoreUrlProvider,
      deepLinkHandler = deepLinkHandler
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      localEventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_SCREEN_IMPRESSION, GeneralEventTrackerScreenId.SPLASH_SCREEN)
      )

      awaitBodyMock<HomeUiProps> {
        account.shouldBe(SoftwareAccountMock)
      }

      homeUiStateMachine.shouldShowPlatformNfcScreen = true

      awaitItemMaybe(timeout = 100.milliseconds).shouldBe(null)

      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("DSV2 iOS still replaces the current screen for sign transaction models that keep custom background") {

    val deviceInfoProvider = DeviceInfoProviderMock().apply {
      devicePlatformValue = DevicePlatform.IOS
    }
    val localEventTracker = EventTrackerMock { name ->
      turbines.create("$name dsv2 ios sign transaction custom background")
    }
    val homeUiStateMachine = ToggleableSignTransactionHomeUiStateMachine(showNativeSheetOnIos = false)
    loadAppService.appState.value = HasActiveSoftwareAccount(
      account = SoftwareAccountMock
    )

    stateMachine = AppUiStateMachineImpl(
      appVariant = AppVariant.Development,
      navigatorPresenter = navigatorPresenter,
      eventTracker = localEventTracker,
      homeUiStateMachine = homeUiStateMachine,
      liteHomeUiStateMachine = object : LiteHomeUiStateMachine,
        ScreenStateMachineMock<LiteHomeUiProps>(id = "lite-home") {},
      fullAccountUiStateMachine = fullAccountUiStateMachine,
      createAccountUiStateMachine = createAccountUiStateMachine,
      noActiveAccountUiStateMachine = noActiveAccountUiStateMachine,
      loadAppService = loadAppService,
      createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
      liteAccountCloudBackupRestorationUiStateMachine = liteAccountCloudBackupRestorationUiStateMachine,
      appWorkerExecutor = appWorkerExecutor,
      accountService = AccountServiceFake(),
      datadogRumMonitor = datadogRumMonitor,
      splashScreenDelay = SplashScreenDelay(10.milliseconds),
      welcomeToBitkeyScreenDuration = WelcomeToBitkeyScreenDuration(10.milliseconds),
      deviceInfoProvider = deviceInfoProvider,
      appUpdateModalFeatureFlag = appUpdateModalFeatureFlag,
      appStoreUrlProvider = appStoreUrlProvider,
      deepLinkHandler = deepLinkHandler
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      localEventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_SCREEN_IMPRESSION, GeneralEventTrackerScreenId.SPLASH_SCREEN)
      )

      awaitBodyMock<HomeUiProps> {
        account.shouldBe(SoftwareAccountMock)
      }

      homeUiStateMachine.shouldShowPlatformNfcScreen = true

      awaitUntilBody<SignTransactionNfcBodyModel> {
        status.shouldBe(SignTransactionNfcBodyModel.Status.Searching)
      }

      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("screen log is suppressed when eventTrackerShouldTrack is false") {
    val localEventTracker = EventTrackerMock { name ->
      turbines.create("$name suppress screen log")
    }
    val homeUiStateMachine = ToggleableShouldTrackHomeUiStateMachine()
    loadAppService.appState.value = HasActiveSoftwareAccount(
      account = SoftwareAccountMock
    )

    stateMachine = AppUiStateMachineImpl(
      appVariant = AppVariant.Development,
      navigatorPresenter = navigatorPresenter,
      eventTracker = localEventTracker,
      homeUiStateMachine = homeUiStateMachine,
      liteHomeUiStateMachine = object : LiteHomeUiStateMachine,
        ScreenStateMachineMock<LiteHomeUiProps>(id = "lite-home") {},
      fullAccountUiStateMachine = fullAccountUiStateMachine,
      createAccountUiStateMachine = createAccountUiStateMachine,
      noActiveAccountUiStateMachine = noActiveAccountUiStateMachine,
      loadAppService = loadAppService,
      createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
      liteAccountCloudBackupRestorationUiStateMachine = liteAccountCloudBackupRestorationUiStateMachine,
      appWorkerExecutor = appWorkerExecutor,
      accountService = AccountServiceFake(),
      datadogRumMonitor = datadogRumMonitor,
      splashScreenDelay = SplashScreenDelay(10.milliseconds),
      welcomeToBitkeyScreenDuration = WelcomeToBitkeyScreenDuration(10.milliseconds),
      deviceInfoProvider = DeviceInfoProviderMock(),
      appUpdateModalFeatureFlag = appUpdateModalFeatureFlag,
      appStoreUrlProvider = appStoreUrlProvider,
      deepLinkHandler = deepLinkHandler
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      localEventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_SCREEN_IMPRESSION, GeneralEventTrackerScreenId.SPLASH_SCREEN)
      )

      awaitBodyMock<HomeUiProps> {
        account.shouldBe(SoftwareAccountMock)
      }

      // Clear logs from splash and home screens before switching to non-tracked screen
      logWriter.clear()

      // Switch to a screen with eventTrackerShouldTrack = false
      homeUiStateMachine.shouldShowNonTrackedScreen = true

      awaitUntilBody<BodyModelMock<*>> {
        id.shouldBe("non-tracked-screen")
      }

      // Verify no "Screen" tag log was emitted for the non-tracked screen
      logWriter.logs.none { it.tag == "Screen" }.shouldBe(true)

      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

})

private class ToggleablePlatformNfcHomeUiStateMachine(
  private val showNativeSheetOnIos: Boolean = true,
) : HomeUiStateMachine {
  var shouldShowPlatformNfcScreen by mutableStateOf(false)

  @Composable
  override fun model(props: HomeUiProps): ScreenModel {
    return when {
      shouldShowPlatformNfcScreen -> {
        NfcBodyModel(
          text = "Hold your Bitkey to the back of your phone",
          status = NfcBodyModel.Status.Searching(onCancel = {}),
          showNativeSheetOnIos = showNativeSheetOnIos,
          eventTrackerScreenInfo = null
        ).asPlatformNfcScreen(
          designSystemV2Enabled = true,
          devicePlatform = DevicePlatform.IOS
        )
      }
      else -> {
        BodyModelMock(
          id = "home",
          latestProps = props
        ).asRootScreen()
      }
    }
  }
}

private class ToggleablePlatformFwupHomeUiStateMachine : HomeUiStateMachine {
  var shouldShowPlatformNfcScreen by mutableStateOf(false)
  var bodyModel: Any? by mutableStateOf(null)

  @Composable
  override fun model(props: HomeUiProps): ScreenModel {
    return when {
      bodyModel is FwupNextComponentReadyModel -> {
        ScreenModel(body = bodyModel as FwupNextComponentReadyModel)
      }
      bodyModel is FwupNfcBodyModel.Status -> {
        FwupNfcBodyModel(
          onCancel = {},
          status = bodyModel as FwupNfcBodyModel.Status,
          eventTrackerScreenInfo = null
        ).asPlatformNfcScreen(
          designSystemV2Enabled = true,
          devicePlatform = build.wallet.platform.device.DevicePlatform.IOS
        )
      }
      shouldShowPlatformNfcScreen -> {
        FwupNfcBodyModel(
          onCancel = {},
          status = FwupNfcBodyModel.Status.Searching(),
          eventTrackerScreenInfo = null
        ).asPlatformNfcScreen(
          designSystemV2Enabled = true,
          devicePlatform = build.wallet.platform.device.DevicePlatform.IOS
        )
      }
      else -> {
        BodyModelMock(
          id = "home",
          latestProps = props
        ).asRootScreen()
      }
    }
  }
}

private class ToggleableShouldTrackHomeUiStateMachine : HomeUiStateMachine {
  var shouldShowNonTrackedScreen by mutableStateOf(false)

  @Composable
  override fun model(props: HomeUiProps): ScreenModel {
    return when {
      shouldShowNonTrackedScreen -> {
        ScreenModel(
          body = BodyModelMock(
            id = "non-tracked-screen",
            latestProps = props,
            eventTrackerScreenInfo = EventTrackerScreenInfo(
              eventTrackerScreenId = SendEventTrackerScreenId.SEND_ADDRESS_ENTRY,
              eventTrackerShouldTrack = false
            )
          )
        )
      }
      else -> {
        BodyModelMock(
          id = "home",
          latestProps = props
        ).asRootScreen()
      }
    }
  }
}

private class ToggleableSignTransactionHomeUiStateMachine(
  private val showNativeSheetOnIos: Boolean,
) : HomeUiStateMachine {
  var shouldShowPlatformNfcScreen by mutableStateOf(false)

  @Composable
  override fun model(props: HomeUiProps): ScreenModel {
    return when {
      shouldShowPlatformNfcScreen -> {
        ScreenModel(
          body =
            SignTransactionNfcBodyModel(
              onCancel = {},
              status = SignTransactionNfcBodyModel.Status.Searching,
              showNativeSheetOnIos = showNativeSheetOnIos,
              eventTrackerScreenInfo = null
            ),
          presentationStyle = ScreenPresentationStyle.FullScreen,
          themePreference = ThemePreference.Manual(Theme.DARK)
        )
      }
      else -> {
        BodyModelMock(
          id = "home",
          latestProps = props
        ).asRootScreen()
      }
    }
  }
}
