package build.wallet.statemachine.app

import bitkey.datadog.DatadogRumMonitorFake
import bitkey.ui.framework.NavigatorPresenterFake
import build.wallet.account.AccountServiceFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.LOADING_APP
import build.wallet.analytics.v1.Action.ACTION_APP_SCREEN_IMPRESSION
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
import build.wallet.platform.links.AppStoreUrlProviderMock
import build.wallet.platform.links.DeepLinkHandlerMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.account.create.full.CreateAccountUiProps
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachine
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiProps
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiStateMachine
import build.wallet.statemachine.account.full.FullAccountUiProps
import build.wallet.statemachine.account.full.FullAccountUiStateMachine
import build.wallet.statemachine.core.AppUpdateModalBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.SplashBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.*
import build.wallet.statemachine.data.keybox.AccountData.CheckingActiveAccountData
import build.wallet.statemachine.home.full.HomeUiProps
import build.wallet.statemachine.home.full.HomeUiStateMachine
import build.wallet.statemachine.home.lite.LiteHomeUiProps
import build.wallet.statemachine.home.lite.LiteHomeUiStateMachine
import build.wallet.statemachine.recovery.cloud.LiteAccountCloudBackupRestorationUiProps
import build.wallet.statemachine.recovery.cloud.LiteAccountCloudBackupRestorationUiStateMachine
import build.wallet.statemachine.root.*
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilBodyMock
import build.wallet.worker.AppWorkerExecutorMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.DelicateCoroutinesApi
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
  val accountDataStateMachine =
    object : AccountDataStateMachine, StateMachineMock<AccountDataProps, AccountData>(
      initialModel = CheckingActiveAccountData
    ) {}
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

  // Fakes are stateful, need to reinitialize before each test to reset the state.
  beforeTest {
    loadAppService.reset()
    loadAppService.appState.value = AppState.NoActiveAccount
    accountDataStateMachine.reset()
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
        accountDataStateMachine = accountDataStateMachine,
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

  test("Loading while checking for account data") {
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("ActiveKeyboxLoadedData") {
    loadAppService.appState.value = AppState.HasActiveFullAccount(
      account = FullAccountMock,
      pendingAuthKeyRotation = null
    )
    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitBodyMock<FullAccountUiProps> {
        accountData.shouldBe(ActiveKeyboxLoadedDataMock)
      }

      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Loading until LoadAppService returns the state") {
    loadAppService.appState.value = null

    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = AppState.HasActiveFullAccount(
        account = FullAccountMock,
        pendingAuthKeyRotation = null
      )

      awaitBodyMock<FullAccountUiProps> {
        accountData.shouldBe(ActiveKeyboxLoadedDataMock)
      }

      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("NoActiveAccount shows NoActiveAccountUiStateMachine") {
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitBodyMock<NoActiveAccountUiProps>()

      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("existing software account") {
    loadAppService.appState.value = null

    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)
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

    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)
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
        accountData.shouldBe(ActiveKeyboxLoadedDataMock)
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
    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)

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
        accountData.shouldBe(ActiveKeyboxLoadedDataMock)
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
    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)

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
    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)

    stateMachine.test(Unit) {
      awaitBody<AppUpdateModalBodyModel> {
        onCancel()
      }

      awaitUntilBodyMock<FullAccountUiProps> {
        accountData.shouldBe(ActiveKeyboxLoadedDataMock)
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
    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      awaitBodyMock<FullAccountUiProps> {
        accountData.shouldBe(ActiveKeyboxLoadedDataMock)
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
      accountDataStateMachine = accountDataStateMachine,
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

    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)
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
        accountData.shouldBe(ActiveKeyboxLoadedDataMock)
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
    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)

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
      accountDataStateMachine = accountDataStateMachine,
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
        accountData.shouldBe(ActiveKeyboxLoadedDataMock)
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
})
