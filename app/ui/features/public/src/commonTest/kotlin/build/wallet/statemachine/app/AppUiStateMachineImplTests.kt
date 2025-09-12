package build.wallet.statemachine.app

import androidx.compose.runtime.Composable
import bitkey.datadog.DatadogRumMonitorFake
import bitkey.ui.framework.NavigatorPresenterFake
import bitkey.ui.statemachine.interstitial.InterstitialUiProps
import build.wallet.account.AccountServiceFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_APP_SCREEN_IMPRESSION
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keybox.SoftwareAccountMock
import build.wallet.bootstrap.AppState
import build.wallet.bootstrap.AppState.HasActiveSoftwareAccount
import build.wallet.bootstrap.LoadAppServiceFake
import build.wallet.cloud.backup.CloudBackupV2WithLiteAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.inappsecurity.BiometricAuthServiceFake
import build.wallet.platform.config.AppVariant
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.account.ChooseAccountAccessUiProps
import build.wallet.statemachine.account.ChooseAccountAccessUiStateMachine
import build.wallet.statemachine.account.create.full.CreateAccountUiProps
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachine
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiProps
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiStateMachine
import build.wallet.statemachine.biometric.BiometricPromptProps
import build.wallet.statemachine.biometric.BiometricPromptUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SplashBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.statemachine.data.keybox.AccountData.CheckingActiveAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.GettingStartedData
import build.wallet.statemachine.data.keybox.AccountDataProps
import build.wallet.statemachine.data.keybox.AccountDataStateMachine
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData
import build.wallet.statemachine.home.full.HomeUiProps
import build.wallet.statemachine.home.full.HomeUiStateMachine
import build.wallet.statemachine.home.lite.LiteHomeUiProps
import build.wallet.statemachine.home.lite.LiteHomeUiStateMachine
import build.wallet.statemachine.recovery.cloud.*
import build.wallet.statemachine.recovery.conflict.NoLongerRecoveringUiProps
import build.wallet.statemachine.recovery.conflict.NoLongerRecoveringUiStateMachine
import build.wallet.statemachine.recovery.conflict.SomeoneElseIsRecoveringUiProps
import build.wallet.statemachine.recovery.conflict.SomeoneElseIsRecoveringUiStateMachine
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitRecoveryUiStateMachine
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitRecoveryUiStateMachineProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiStateMachine
import build.wallet.statemachine.root.AppUiStateMachineImpl
import build.wallet.statemachine.root.SplashScreenDelay
import build.wallet.statemachine.root.WelcomeToBitkeyScreenDuration
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.worker.AppWorkerExecutorMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
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
  val noLongerRecoveringUiStateMachine =
    object : NoLongerRecoveringUiStateMachine,
      ScreenStateMachineMock<NoLongerRecoveringUiProps>(id = "no-longer-recovering") {}
  val someoneElseIsRecoveringUiStateMachine =
    object : SomeoneElseIsRecoveringUiStateMachine,
      ScreenStateMachineMock<SomeoneElseIsRecoveringUiProps>(id = "someone-else-recovering") {}
  val accountDataStateMachine =
    object : AccountDataStateMachine, StateMachineMock<AccountDataProps, AccountData>(
      initialModel = CheckingActiveAccountData
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
  val authKeyRotationUiStateMachine =
    object : RotateAuthKeyUIStateMachine,
      ScreenStateMachineMock<RotateAuthKeyUIStateMachineProps>(
        id = "rotate-auth-key"
      ) {}

  lateinit var stateMachine: AppUiStateMachineImpl

  val appWorkerExecutor = AppWorkerExecutorMock(turbines::create)

  val gettingStartedData = GettingStartedData(
    startLiteAccountCreation = {},
    startRecovery = {},
    startEmergencyExitRecovery = {}
  )

  val biometricAuthService = BiometricAuthServiceFake()

  val datadogRumMonitor = DatadogRumMonitorFake(turbines::create)

  val interstitialUiStateMachine = InterstitialUiStateMachineFake()

  // Fakes are stateful, need to reinitialize before each test to reset the state.
  beforeTest {
    loadAppService.reset()
    loadAppService.appState.value = AppState.Undetermined
    accountDataStateMachine.reset()
    biometricAuthService.reset()
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
        chooseAccountAccessUiStateMachine = object : ChooseAccountAccessUiStateMachine,
          ScreenStateMachineMock<ChooseAccountAccessUiProps>(id = "account-access") {},
        createAccountUiStateMachine = createAccountUiStateMachine,
        accountDataStateMachine = accountDataStateMachine,
        loadAppService = loadAppService,
        noLongerRecoveringUiStateMachine = noLongerRecoveringUiStateMachine,
        someoneElseIsRecoveringUiStateMachine = someoneElseIsRecoveringUiStateMachine,
        accessCloudBackupUiStateMachine = accessCloudBackupUiStateMachine,
        createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
        liteAccountCloudBackupRestorationUiStateMachine =
        liteAccountCloudBackupRestorationUiStateMachine,
        emergencyExitKitRecoveryUiStateMachine = emergencyExitKitRecoveryUiStateMachine,
        authKeyRotationUiStateMachine = authKeyRotationUiStateMachine,
        appWorkerExecutor = appWorkerExecutor,
        biometricPromptUiStateMachine = object : BiometricPromptUiStateMachine {
          @Composable
          override fun model(props: BiometricPromptProps): ScreenModel? {
            return if (props.shouldPromptForAuth) {
              BodyModelMock(
                id = "biometric-prompt",
                latestProps = props
              ).asRootScreen()
            } else {
              null
            }
          }
        },
        biometricAuthService = biometricAuthService,
        accountService = AccountServiceFake(),
        datadogRumMonitor = datadogRumMonitor,
        splashScreenDelay = SplashScreenDelay(10.milliseconds),
        welcomeToBitkeyScreenDuration = WelcomeToBitkeyScreenDuration(10.milliseconds),
        deviceInfoProvider = DeviceInfoProviderMock(),
        interstitialUiStateMachine = interstitialUiStateMachine
      )
    interstitialUiStateMachine.reset()
  }

  suspend fun EventTrackerMock.awaitSplashScreenEvent() {
    eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_SCREEN_IMPRESSION, GeneralEventTrackerScreenId.SPLASH_SCREEN)
    )
  }

  test("Loading while checking for account data") {
    accountDataStateMachine.emitModel(CheckingActiveAccountData)
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      appWorkerExecutor.executeAllCalls.awaitItem()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("ActiveKeyboxLoadedData") {
    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitBodyMock<HomeUiProps> {
        account.shouldBe(ActiveKeyboxLoadedDataMock.account)
        lostHardwareRecoveryData.shouldBe(ActiveKeyboxLoadedDataMock.lostHardwareRecoveryData)
      }

      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("Loading until LoadAppService returns the state") {
    loadAppService.appState.value = null

    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = AppState.Undetermined

      awaitBodyMock<HomeUiProps> {
        account.shouldBe(ActiveKeyboxLoadedDataMock.account)
        lostHardwareRecoveryData.shouldBe(ActiveKeyboxLoadedDataMock.lostHardwareRecoveryData)
      }

      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("ReadyToChooseAccountAccessKeyboxData") {
    accountDataStateMachine.emitModel(gettingStartedData)
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitBodyMock<ChooseAccountAccessUiProps>()

      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("RecoveringKeyboxData") {
    accountDataStateMachine.emitModel(
      NoActiveAccountData.RecoveringAccountData(
        lostAppRecoveryData = LostAppRecoveryData.LostAppRecoveryInProgressData(
          recoveryInProgressData =
            RecoveryInProgressData.WaitingForRecoveryDelayPeriodData(
              factorToRecover = PhysicalFactor.Hardware,
              delayPeriodStartTime = Instant.DISTANT_PAST,
              delayPeriodEndTime = Instant.DISTANT_PAST,
              cancel = {},
              retryCloudRecovery = null
            )
        )
      )
    )
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      awaitBodyMock<LostAppRecoveryUiProps>()
      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("RecoveringAccountWithEmergencyExitKit") {
    accountDataStateMachine.emitModel(
      NoActiveAccountData.RecoveringAccountWithEmergencyExitKit(
        onExit = {}
      )
    )
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitBodyMock<EmergencyExitKitRecoveryUiStateMachineProps>()
      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("NoLongerRecoveringKeyboxData") {
    accountDataStateMachine.emitModel(AccountData.NoLongerRecoveringFullAccountData(App))
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitBodyMock<NoLongerRecoveringUiProps>()
      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("SomeoneElseIsRecoveringKeyboxData") {
    accountDataStateMachine.emitModel(
      AccountData.SomeoneElseIsRecoveringFullAccountData(
        data = SomeoneElseIsRecoveringData.ShowingSomeoneElseIsRecoveringData(App, {}),
        fullAccountId = FullAccountIdMock
      )
    )
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitBodyMock<SomeoneElseIsRecoveringUiProps>()
      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("existing software account") {
    loadAppService.appState.value = null

    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = HasActiveSoftwareAccount(SoftwareAccountMock)

      awaitBodyMock<HomeUiProps> {
        account.shouldBe(SoftwareAccountMock)
      }
      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("create a new software account") {
    loadAppService.appState.value = null

    accountDataStateMachine.emitModel(gettingStartedData)
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = AppState.Undetermined

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
    }
  }

  test("ActiveKeyboxLoadedData w/ biometric auth required") {
    biometricAuthService.isBiometricAuthRequiredFlow.value = true

    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)
    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitBodyMock<BiometricPromptProps>()
      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("Launching from an onboarding account") {
    loadAppService.appState.value = AppState.OnboardingFullAccount(account = FullAccountMock)

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      awaitBodyMock<CreateAccountUiProps> {
        onOnboardingComplete(FullAccountMock)
      }

      accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)

      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Welcome to Bitkey")
      }

      awaitBodyMock<HomeUiProps> {
        account.shouldBe(FullAccountMock)
      }
      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("creating a lite account") {
    loadAppService.appState.value = null

    accountDataStateMachine.emitModel(
      NoActiveAccountData.CheckingCloudBackupData(
        intent = AccountData.StartIntent.BeTrustedContact,
        inviteCode = "invite-code",
        onStartCloudRecovery = {},
        onStartLostAppRecovery = {},
        onImportEmergencyExitKit = {},
        onExit = {}
      )
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = AppState.Undetermined

      awaitBodyMock<AccessCloudBackupUiProps> {
        onStartLiteAccountCreation("invite-code", AccountData.StartIntent.BeTrustedContact)
      }

      awaitBodyMock<CreateLiteAccountUiProps> {
        onAccountCreated(LiteAccountMock)
      }

      awaitBodyMock<LiteHomeUiProps>()
      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("recovering a lite account") {
    loadAppService.appState.value = null

    accountDataStateMachine.emitModel(
      NoActiveAccountData.CheckingCloudBackupData(
        intent = AccountData.StartIntent.BeTrustedContact,
        inviteCode = "invite-code",
        onStartCloudRecovery = {},
        onStartLostAppRecovery = {},
        onImportEmergencyExitKit = {},
        onExit = {}
      )
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = AppState.Undetermined

      awaitBodyMock<AccessCloudBackupUiProps> {
        onStartLiteAccountRecovery(CloudBackupV2WithLiteAccountMock)
      }

      awaitBodyMock<LiteAccountCloudBackupRestorationUiProps> {
        onLiteAccountRestored(LiteAccountMock)
      }

      awaitBodyMock<LiteHomeUiProps>()
      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("creating a full account") {
    loadAppService.appState.value = null
    accountDataStateMachine.emitModel(gettingStartedData)

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = AppState.Undetermined

      awaitBodyMock<ChooseAccountAccessUiProps> {
        onCreateFullAccount()
      }

      awaitBodyMock<CreateAccountUiProps> {
        onOnboardingComplete(FullAccountMock)
      }

      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Welcome to Bitkey")
      }

      // This is a quirk in mocking the DSM, since once we create the full acount we go back to
      // rendering via the DSM. Once the DSM is done being refactored, this will be Money Home
      awaitBodyMock<ChooseAccountAccessUiProps>()
      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("no interstitial shown after onboarding from existing full account when interstitials are set to show") {
    loadAppService.appState.value = AppState.OnboardingFullAccount(account = FullAccountMock)
    interstitialUiStateMachine.shouldShowInterstitial = true

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      awaitBodyMock<CreateAccountUiProps> {
        onOnboardingComplete(FullAccountMock)
      }

      accountDataStateMachine.emitModel(
        ActiveKeyboxLoadedDataMock
      )

      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Welcome to Bitkey")
      }

      awaitBodyMock<HomeUiProps> {
        account.shouldBe(FullAccountMock)
      }
      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("no interstitial shown after upgrading lite account when interstitials are set to show") {
    loadAppService.appState.value = AppState.HasActiveLiteAccount(
      account = LiteAccountMock
    )
    interstitialUiStateMachine.shouldShowInterstitial = true

    accountDataStateMachine.emitModel(
      ActiveKeyboxLoadedDataMock
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      awaitBodyMock<LiteHomeUiProps>("lite-home") {
        onUpgradeComplete(FullAccountMock)
      }

      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Welcome to Bitkey")
      }

      awaitBodyMock<HomeUiProps> {
        account.shouldBe(FullAccountMock)
      }
      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("no interstitial shown after onboarding lite account to full when interstitials are set to show") {
    loadAppService.appState.value = AppState.LiteAccountOnboardingToFullAccount(
      activeAccount = LiteAccountMock,
      onboardingAccount = FullAccountMock
    )
    interstitialUiStateMachine.shouldShowInterstitial = true

    accountDataStateMachine.emitModel(
      ActiveKeyboxLoadedDataMock
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      awaitBodyMock<CreateAccountUiProps> {
        onOnboardingComplete(FullAccountMock)
      }

      accountDataStateMachine.emitModel(
        ActiveKeyboxLoadedDataMock
      )

      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Welcome to Bitkey")
      }

      awaitBodyMock<HomeUiProps> {
        account.shouldBe(FullAccountMock)
      }
      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("interstitial is shown after launching from existing full account when interstitials are set to show") {
    loadAppService.appState.value = AppState.HasActiveFullAccount(
      account = FullAccountMock,
      pendingAuthKeyRotation = null
    )
    interstitialUiStateMachine.shouldShowInterstitial = true

    accountDataStateMachine.emitModel(
      ActiveKeyboxLoadedDataMock
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      awaitBodyMock<InterstitialUiProps>(InterstitialUiStateMachineFake.BODY_MODEL_ID) {
        account.shouldBe(FullAccountMock)
        isComingFromOnboarding.shouldBe(false)
      }
      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("interstitial is not shown after launching from existing full account") {
    loadAppService.appState.value = AppState.HasActiveFullAccount(
      account = FullAccountMock,
      pendingAuthKeyRotation = null
    )
    interstitialUiStateMachine.shouldShowInterstitial = false

    accountDataStateMachine.emitModel(
      ActiveKeyboxLoadedDataMock
    )

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      awaitBodyMock<HomeUiProps> {
        account.shouldBe(FullAccountMock)
      }
      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }

  test("no interstitial shown after onboarding from null app state when interstitials are set to show") {
    loadAppService.appState.value = null
    interstitialUiStateMachine.shouldShowInterstitial = true
    accountDataStateMachine.emitModel(gettingStartedData)

    stateMachine.test(Unit) {
      awaitBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()

      loadAppService.appState.value = AppState.Undetermined

      awaitBodyMock<ChooseAccountAccessUiProps> {
        onCreateFullAccount()
      }

      awaitBodyMock<CreateAccountUiProps> {
        onOnboardingComplete(FullAccountMock)
      }

      accountDataStateMachine.emitModel(
        ActiveKeyboxLoadedDataMock
      )

      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Welcome to Bitkey")
      }

      awaitBodyMock<HomeUiProps> {
        account.shouldBe(FullAccountMock)
      }
      appWorkerExecutor.executeAllCalls.awaitItem()
    }
  }
})
