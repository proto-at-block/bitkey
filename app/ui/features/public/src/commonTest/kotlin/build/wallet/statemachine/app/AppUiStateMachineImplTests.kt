package build.wallet.statemachine.app

import bitkey.datadog.DatadogRumMonitorFake
import bitkey.ui.framework.NavigatorPresenterFake
import build.wallet.account.AccountServiceFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.LOADING_APP
import build.wallet.analytics.v1.Action.ACTION_APP_SCREEN_IMPRESSION
import build.wallet.availability.AgeRangeVerificationResult
import build.wallet.availability.AgeRangeVerificationServiceFake
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
import build.wallet.statemachine.account.ChooseAccountAccessUiProps
import build.wallet.statemachine.account.ChooseAccountAccessUiStateMachine
import build.wallet.statemachine.account.create.full.CreateAccountUiProps
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachine
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiProps
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiStateMachine
import build.wallet.statemachine.account.full.FullAccountUiProps
import build.wallet.statemachine.account.full.FullAccountUiStateMachine
import build.wallet.statemachine.core.AgeRestrictedBodyModel
import build.wallet.statemachine.core.AppUpdateModalBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.SplashBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.*
import build.wallet.statemachine.data.keybox.AccountData.CheckingActiveAccountData
import build.wallet.statemachine.data.keybox.NoActiveAccountData.GettingStartedData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData
import build.wallet.statemachine.home.full.HomeUiProps
import build.wallet.statemachine.home.full.HomeUiStateMachine
import build.wallet.statemachine.home.lite.LiteHomeUiProps
import build.wallet.statemachine.home.lite.LiteHomeUiStateMachine
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiProps
import build.wallet.statemachine.recovery.cloud.AccessCloudBackupUiStateMachine
import build.wallet.statemachine.recovery.cloud.LiteAccountCloudBackupRestorationUiProps
import build.wallet.statemachine.recovery.cloud.LiteAccountCloudBackupRestorationUiStateMachine
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitRecoveryUiStateMachine
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitRecoveryUiStateMachineProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiStateMachine
import build.wallet.statemachine.root.AppUiStateMachineImpl
import build.wallet.statemachine.root.SplashScreenDelay
import build.wallet.statemachine.root.WelcomeToBitkeyScreenDuration
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilBodyMock
import build.wallet.worker.AppWorkerExecutorMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.milliseconds

class AppUiStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)
  val navigatorPresenter = NavigatorPresenterFake()

  val recoveringKeyboxUiStateMachine =
    object : LostAppRecoveryUiStateMachine,
      ScreenStateMachineMock<LostAppRecoveryUiProps>(id = "recover-app") {}
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
  val noActiveAccountDataStateMachine =
    object : NoActiveAccountDataStateMachine,
      StateMachineMock<NoActiveAccountDataProps, NoActiveAccountData>(
        initialModel = NoActiveAccountData.CheckingRecovery
      ) {}
  val loadAppService = LoadAppServiceFake()
  val accessCloudBackupUiStateMachine =
    object : AccessCloudBackupUiStateMachine,
      ScreenStateMachineMock<AccessCloudBackupUiProps>(id = "access-cloud-backup") {}
  val liteAccountCloudBackupRestorationUiStateMachine =
    object : LiteAccountCloudBackupRestorationUiStateMachine,
      ScreenStateMachineMock<LiteAccountCloudBackupRestorationUiProps>(
        id = "recover-lite-account"
      ) {}
  val emergencyExitKitRecoveryUiStateMachine =
    object : EmergencyExitKitRecoveryUiStateMachine,
      ScreenStateMachineMock<EmergencyExitKitRecoveryUiStateMachineProps>(
        id = "emergencey-access-kit-recovery"
      ) {}

  lateinit var stateMachine: AppUiStateMachineImpl

  val appWorkerExecutor = AppWorkerExecutorMock(turbines::create)

  val gettingStartedData = GettingStartedData(
    startLiteAccountCreation = {},
    startRecovery = {},
    startEmergencyExitRecovery = {}
  )

  val datadogRumMonitor = DatadogRumMonitorFake(turbines::create)

  val deepLinkHandler = DeepLinkHandlerMock(turbines::create)

  val appStoreUrlProvider = AppStoreUrlProviderMock()

  val appUpdateModalFeatureFlag = AppUpdateModalFeatureFlag(FeatureFlagDaoFake())

  val ageRangeVerificationService = AgeRangeVerificationServiceFake()

  // Fakes are stateful, need to reinitialize before each test to reset the state.
  beforeTest {
    loadAppService.reset()
    loadAppService.appState.value = AppState.NoActiveAccount
    accountDataStateMachine.reset()
    noActiveAccountDataStateMachine.reset()
    deepLinkHandler.reset()
    appStoreUrlProvider.reset()
    ageRangeVerificationService.reset()
    stateMachine =
      AppUiStateMachineImpl(
        appVariant = AppVariant.Development,
        navigatorPresenter = navigatorPresenter,
        eventTracker = eventTracker,
        lostAppRecoveryUiStateMachine = recoveringKeyboxUiStateMachine,
        homeUiStateMachine = object : HomeUiStateMachine,
          ScreenStateMachineMock<HomeUiProps>(id = "home") {},
        liteHomeUiStateMachine = object : LiteHomeUiStateMachine,
          ScreenStateMachineMock<LiteHomeUiProps>(id = "lite-home") {},
        fullAccountUiStateMachine = fullAccountUiStateMachine,
        chooseAccountAccessUiStateMachine = object : ChooseAccountAccessUiStateMachine,
          ScreenStateMachineMock<ChooseAccountAccessUiProps>(id = "account-access") {},
        createAccountUiStateMachine = createAccountUiStateMachine,
        accountDataStateMachine = accountDataStateMachine,
        noActiveAccountDataStateMachine = noActiveAccountDataStateMachine,
        loadAppService = loadAppService,
        accessCloudBackupUiStateMachine = accessCloudBackupUiStateMachine,
        createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
        liteAccountCloudBackupRestorationUiStateMachine =
        liteAccountCloudBackupRestorationUiStateMachine,
        emergencyExitKitRecoveryUiStateMachine = emergencyExitKitRecoveryUiStateMachine,
        appWorkerExecutor = appWorkerExecutor,
        accountService = AccountServiceFake(),
        datadogRumMonitor = datadogRumMonitor,
        splashScreenDelay = SplashScreenDelay(10.milliseconds),
        welcomeToBitkeyScreenDuration = WelcomeToBitkeyScreenDuration(10.milliseconds),
        deviceInfoProvider = DeviceInfoProviderMock(),
        appUpdateModalFeatureFlag = appUpdateModalFeatureFlag,
        appStoreUrlProvider = appStoreUrlProvider,
        deepLinkHandler = deepLinkHandler,
        ageRangeVerificationService = ageRangeVerificationService
      )
  }

  suspend fun EventTrackerMock.awaitSplashScreenEvent() {
    eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_SCREEN_IMPRESSION, GeneralEventTrackerScreenId.SPLASH_SCREEN)
    )
  }

  test("Loading while checking for account data") {
    noActiveAccountDataStateMachine.emitModel(NoActiveAccountData.CheckingRecovery)
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Age range verification denied shows AgeRestrictedBodyModel") {
    ageRangeVerificationService.result = AgeRangeVerificationResult.Denied
    noActiveAccountDataStateMachine.emitModel(gettingStartedData)

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      awaitBody<AgeRestrictedBodyModel>()
      appWorkerExecutor.executeAllCalls.awaitItem()
      eventTracker.eventCalls.awaitItem()
        .shouldBe(TrackedAction(ACTION_APP_SCREEN_IMPRESSION, GeneralEventTrackerScreenId.AGE_RESTRICTED))
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
      eventTracker.eventCalls.awaitItem()
        .shouldBe(TrackedAction(ACTION_APP_SCREEN_IMPRESSION, LOADING_APP))
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
      eventTracker.eventCalls.awaitItem()
        .shouldBe(TrackedAction(ACTION_APP_SCREEN_IMPRESSION, LOADING_APP))
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("ReadyToChooseAccountAccessKeyboxData") {
    noActiveAccountDataStateMachine.emitModel(gettingStartedData)
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitBodyMock<ChooseAccountAccessUiProps>()

      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("RecoveringKeyboxData") {
    noActiveAccountDataStateMachine.emitModel(
      NoActiveAccountData.RecoveringAccountData(
        lostAppRecoveryData = LostAppRecoveryData.LostAppRecoveryInProgressData(
          recoveryInProgressData =
            RecoveryInProgressData.WaitingForRecoveryDelayPeriodData(
              factorToRecover = build.wallet.bitkey.factor.PhysicalFactor.Hardware,
              delayPeriodStartTime = Instant.DISTANT_PAST,
              delayPeriodEndTime = Instant.DISTANT_PAST,
              cancel = {}
            )
        )
      )
    )
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      awaitBodyMock<LostAppRecoveryUiProps>()
      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("RecoveringAccountWithEmergencyExitKit") {
    noActiveAccountDataStateMachine.emitModel(
      NoActiveAccountData.RecoveringAccountWithEmergencyExitKit(
        onExit = {}
      )
    )
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitBodyMock<EmergencyExitKitRecoveryUiStateMachineProps>()
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
    noActiveAccountDataStateMachine.emitModel(gettingStartedData)

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = AppState.NoActiveAccount

      awaitBodyMock<ChooseAccountAccessUiProps> {
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
      eventTracker.eventCalls.awaitItem()
        .shouldBe(TrackedAction(ACTION_APP_SCREEN_IMPRESSION, LOADING_APP))
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("creating a lite account") {
    loadAppService.appState.value = null

    noActiveAccountDataStateMachine.emitModel(
      NoActiveAccountData.CheckingCloudBackupData(
        intent = StartIntent.BeTrustedContact,
        inviteCode = "invite-code",
        onStartCloudRecovery = { _, _ -> },
        onStartLostAppRecovery = {},
        onImportEmergencyExitKit = {},
        onExit = {}
      )
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = AppState.NoActiveAccount

      awaitBodyMock<AccessCloudBackupUiProps> {
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

    noActiveAccountDataStateMachine.emitModel(
      NoActiveAccountData.CheckingCloudBackupData(
        intent = StartIntent.BeTrustedContact,
        inviteCode = "invite-code",
        onStartCloudRecovery = { _, _ -> },
        onStartLostAppRecovery = {},
        onImportEmergencyExitKit = {},
        onExit = {}
      )
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = AppState.NoActiveAccount

      awaitBodyMock<AccessCloudBackupUiProps> {
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
    noActiveAccountDataStateMachine.emitModel(gettingStartedData)
    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = AppState.NoActiveAccount

      awaitBodyMock<ChooseAccountAccessUiProps> {
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

      // Age verification calls a suspend function inside produceState. There's a race
      // condition between the suspend function completing and Compose recomposition:
      // the LOADING_APP screen may or may not render (and emit its analytics event)
      // depending on whether recomposition happens before or after the result arrives.
      // Note: LOADING_APP screen is visually identical to the splash screen - it shows
      // the same UI but indicates background work (like age verification) is in progress.
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
      eventTracker.eventCalls.awaitItem()
        .shouldBe(TrackedAction(ACTION_APP_SCREEN_IMPRESSION, LOADING_APP))
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

      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      awaitBodyMock<FullAccountUiProps> {
        accountData.shouldBe(ActiveKeyboxLoadedDataMock)
      }

      eventTracker.eventCalls.awaitItem()
        .shouldBe(TrackedAction(ACTION_APP_SCREEN_IMPRESSION, LOADING_APP))

      appWorkerExecutor.executeAllCalls.awaitItem()
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

      eventTracker.eventCalls.awaitItem()

      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("NoActiveAccountDataScreenModel transitions to ViewingFullAccount when account becomes active") {
    val accountService = AccountServiceFake()
    loadAppService.appState.value = AppState.NoActiveAccount
    noActiveAccountDataStateMachine.emitModel(gettingStartedData)

    // Reinitialize stateMachine with the accountService we can control
    stateMachine = AppUiStateMachineImpl(
      appVariant = AppVariant.Development,
      navigatorPresenter = navigatorPresenter,
      eventTracker = eventTracker,
      lostAppRecoveryUiStateMachine = recoveringKeyboxUiStateMachine,
      homeUiStateMachine = object : HomeUiStateMachine,
        ScreenStateMachineMock<HomeUiProps>(id = "home") {},
      liteHomeUiStateMachine = object : LiteHomeUiStateMachine,
        ScreenStateMachineMock<LiteHomeUiProps>(id = "lite-home") {},
      fullAccountUiStateMachine = fullAccountUiStateMachine,
      chooseAccountAccessUiStateMachine = object : ChooseAccountAccessUiStateMachine,
        ScreenStateMachineMock<ChooseAccountAccessUiProps>(id = "account-access") {},
      createAccountUiStateMachine = createAccountUiStateMachine,
      accountDataStateMachine = accountDataStateMachine,
      noActiveAccountDataStateMachine = noActiveAccountDataStateMachine,
      loadAppService = loadAppService,
      accessCloudBackupUiStateMachine = accessCloudBackupUiStateMachine,
      createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
      liteAccountCloudBackupRestorationUiStateMachine = liteAccountCloudBackupRestorationUiStateMachine,
      emergencyExitKitRecoveryUiStateMachine = emergencyExitKitRecoveryUiStateMachine,
      appWorkerExecutor = appWorkerExecutor,
      accountService = accountService,
      datadogRumMonitor = datadogRumMonitor,
      splashScreenDelay = SplashScreenDelay(10.milliseconds),
      welcomeToBitkeyScreenDuration = WelcomeToBitkeyScreenDuration(10.milliseconds),
      deviceInfoProvider = DeviceInfoProviderMock(),
      appUpdateModalFeatureFlag = appUpdateModalFeatureFlag,
      appStoreUrlProvider = appStoreUrlProvider,
      deepLinkHandler = deepLinkHandler,
      ageRangeVerificationService = ageRangeVerificationService
    )

    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      // Should show ChooseAccountAccessScreen initially
      awaitBodyMock<ChooseAccountAccessUiProps>()

      // Simulate account becoming active
      accountService.setActiveAccount(FullAccountMock)

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
      lostAppRecoveryUiStateMachine = recoveringKeyboxUiStateMachine,
      homeUiStateMachine = object : HomeUiStateMachine,
        ScreenStateMachineMock<HomeUiProps>(id = "home") {},
      liteHomeUiStateMachine = object : LiteHomeUiStateMachine,
        ScreenStateMachineMock<LiteHomeUiProps>(id = "lite-home") {},
      fullAccountUiStateMachine = fullAccountUiStateMachine,
      chooseAccountAccessUiStateMachine = object : ChooseAccountAccessUiStateMachine,
        ScreenStateMachineMock<ChooseAccountAccessUiProps>(id = "account-access") {},
      createAccountUiStateMachine = createAccountUiStateMachine,
      accountDataStateMachine = accountDataStateMachine,
      noActiveAccountDataStateMachine = noActiveAccountDataStateMachine,
      loadAppService = loadAppService,
      accessCloudBackupUiStateMachine = accessCloudBackupUiStateMachine,
      createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
      liteAccountCloudBackupRestorationUiStateMachine = liteAccountCloudBackupRestorationUiStateMachine,
      emergencyExitKitRecoveryUiStateMachine = emergencyExitKitRecoveryUiStateMachine,
      appWorkerExecutor = appWorkerExecutor,
      accountService = accountService,
      datadogRumMonitor = datadogRumMonitor,
      splashScreenDelay = SplashScreenDelay(10.milliseconds),
      welcomeToBitkeyScreenDuration = WelcomeToBitkeyScreenDuration(10.milliseconds),
      deviceInfoProvider = DeviceInfoProviderMock(),
      appUpdateModalFeatureFlag = appUpdateModalFeatureFlag,
      appStoreUrlProvider = appStoreUrlProvider,
      deepLinkHandler = deepLinkHandler,
      ageRangeVerificationService = ageRangeVerificationService
    )

    // Set initial account
    accountService.setActiveAccount(FullAccountMock)

    noActiveAccountDataStateMachine.emitModel(gettingStartedData)
    stateMachine.test(Unit) {
      awaitUntilBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      // Should show FullAccountUiProps initially
      awaitUntilBodyMock<FullAccountUiProps> {
        accountData.shouldBe(ActiveKeyboxLoadedDataMock)
      }

      // Simulate account being cleared
      accountService.clear()

      // Should transition to ChooseAccountAccessScreen
      awaitUntilBodyMock<ChooseAccountAccessUiProps> {
        chooseAccountAccessData.shouldBeInstanceOf<GettingStartedData>()
      }

      appWorkerExecutor.executeAllCalls.awaitItem()
      eventTracker.eventCalls.cancelAndIgnoreRemainingEvents()
      cancelAndIgnoreRemainingEvents()
    }
  }
})
