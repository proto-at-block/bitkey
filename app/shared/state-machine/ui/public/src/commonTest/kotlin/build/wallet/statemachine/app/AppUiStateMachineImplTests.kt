package build.wallet.statemachine.app

import androidx.compose.runtime.Composable
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_APP_SCREEN_IMPRESSION
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.SoftwareAccountMock
import build.wallet.bootstrap.AppState
import build.wallet.bootstrap.AppState.HasActiveSoftwareAccount
import build.wallet.bootstrap.LoadAppServiceFake
import build.wallet.cloud.backup.CloudBackupV2WithLiteAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.debug.DebugOptions
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.inappsecurity.BiometricAuthServiceFake
import build.wallet.platform.config.AppVariant
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
import build.wallet.statemachine.core.*
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardingAccountData
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.statemachine.data.keybox.AccountData.CheckingActiveAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.GettingStartedData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.RecoveringAccountWithEmergencyAccessKit
import build.wallet.statemachine.data.keybox.AccountDataStateMachine
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData
import build.wallet.statemachine.dev.DebugMenuProps
import build.wallet.statemachine.dev.DebugMenuStateMachine
import build.wallet.statemachine.home.full.HomeUiProps
import build.wallet.statemachine.home.full.HomeUiStateMachine
import build.wallet.statemachine.home.lite.LiteHomeUiProps
import build.wallet.statemachine.home.lite.LiteHomeUiStateMachine
import build.wallet.statemachine.recovery.cloud.LiteAccountCloudBackupRestorationUiProps
import build.wallet.statemachine.recovery.cloud.LiteAccountCloudBackupRestorationUiStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachineProps
import build.wallet.statemachine.recovery.conflict.NoLongerRecoveringUiProps
import build.wallet.statemachine.recovery.conflict.NoLongerRecoveringUiStateMachine
import build.wallet.statemachine.recovery.conflict.SomeoneElseIsRecoveringUiProps
import build.wallet.statemachine.recovery.conflict.SomeoneElseIsRecoveringUiStateMachine
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitRecoveryUiStateMachine
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitRecoveryUiStateMachineProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiStateMachine
import build.wallet.statemachine.root.AppUiStateMachineImpl
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceProps
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceUiStateMachine
import build.wallet.statemachine.start.GettingStartedRoutingProps
import build.wallet.statemachine.start.GettingStartedRoutingStateMachine
import build.wallet.time.Delayer
import build.wallet.worker.AppWorkerExecutorMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class AppUiStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)

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
    object : AccountDataStateMachine, StateMachineMock<Unit, AccountData>(
      initialModel = CheckingActiveAccountData
    ) {}
  val loadAppService = LoadAppServiceFake()
  val gettingStartedRoutingStateMachine =
    object : GettingStartedRoutingStateMachine,
      ScreenStateMachineMock<GettingStartedRoutingProps>(id = "getting-started") {}
  val liteAccountCloudBackupRestorationUiStateMachine =
    object : LiteAccountCloudBackupRestorationUiStateMachine,
      ScreenStateMachineMock<LiteAccountCloudBackupRestorationUiProps>(
        id = "recover-lite-account"
      ) {}
  val emergencyAccessKitRecoveryUiStateMachine =
    object : EmergencyAccessKitRecoveryUiStateMachine,
      ScreenStateMachineMock<EmergencyAccessKitRecoveryUiStateMachineProps>(
        id = "emergencey-access-kit-recovery"
      ) {}
  val authKeyRotationUiStateMachine =
    object : RotateAuthKeyUIStateMachine,
      ScreenStateMachineMock<RotateAuthKeyUIStateMachineProps>(
        id = "rotate-auth-key"
      ) {}
  val wipingDeviceUiStateMachine =
    object : WipingDeviceUiStateMachine,
      ScreenStateMachineMock<WipingDeviceProps>(id = "wiping-device") {}

  lateinit var stateMachine: AppUiStateMachineImpl

  val appWorkerExecutor = AppWorkerExecutorMock(turbines::create)

  val gettingStartedData = GettingStartedData(
    startFullAccountCreation = {},
    startLiteAccountCreation = {},
    startRecovery = {},
    startEmergencyAccessRecovery = {},
    isNavigatingBack = false,
    wipeExistingDevice = {}
  )

  val biometricAuthService = BiometricAuthServiceFake()

  // Fakes are stateful, need to reinitialize before each test to reset the state.
  beforeTest {
    loadAppService.reset()
    loadAppService.appState.value = AppState.Undetermined
    accountDataStateMachine.reset()
    stateMachine =
      AppUiStateMachineImpl(
        appVariant = AppVariant.Development,
        delayer = Delayer.Default,
        debugMenuStateMachine =
          object : DebugMenuStateMachine, ScreenStateMachineMock<DebugMenuProps>(
            id = "debug-menu"
          ) {},
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
        gettingStartedRoutingStateMachine = gettingStartedRoutingStateMachine,
        createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
        liteAccountCloudBackupRestorationUiStateMachine =
        liteAccountCloudBackupRestorationUiStateMachine,
        emergencyAccessKitRecoveryUiStateMachine = emergencyAccessKitRecoveryUiStateMachine,
        authKeyRotationUiStateMachine = authKeyRotationUiStateMachine,
        wipingDeviceUiStateMachine = wipingDeviceUiStateMachine,
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
        biometricAuthService = biometricAuthService
      )
  }

  afterTest {
    appWorkerExecutor.executeAllCalls.awaitItem()
    biometricAuthService.reset()
  }

  suspend fun EventTrackerMock.awaitSplashScreenEvent() {
    eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_SCREEN_IMPRESSION, GeneralEventTrackerScreenId.SPLASH_SCREEN)
    )
  }

  test("Loading while checking for account data") {
    accountDataStateMachine.emitModel(CheckingActiveAccountData)
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("ActiveKeyboxLoadedData") {
    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitScreenWithBodyModelMock<HomeUiProps> {
        account.shouldBe(ActiveKeyboxLoadedDataMock.account)
        lostHardwareRecoveryData.shouldBe(ActiveKeyboxLoadedDataMock.lostHardwareRecoveryData)
      }
    }
  }

  test("Loading until LoadAppService returns the state") {
    loadAppService.appState.value = null

    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = AppState.Undetermined

      awaitScreenWithBodyModelMock<HomeUiProps> {
        account.shouldBe(ActiveKeyboxLoadedDataMock.account)
        lostHardwareRecoveryData.shouldBe(ActiveKeyboxLoadedDataMock.lostHardwareRecoveryData)
      }
    }
  }

  test("CreatingAccountData") {
    accountDataStateMachine.emitModel(
      NoActiveAccountData.CreatingFullAccountData(
        createFullAccountData = OnboardingAccountData(
          keybox = KeyboxMock,
          isSkipCloudBackupInstructions = false,
          onFoundLiteAccountWithDifferentId = {},
          onOverwriteFullAccountCloudBackupWarning = {},
          onOnboardingComplete = {}
        )
      )
    )
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitScreenWithBodyModelMock<CreateAccountUiProps>()
    }
  }

  test("ReadyToChooseAccountAccessKeyboxData") {
    accountDataStateMachine.emitModel(gettingStartedData)
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitScreenWithBodyModelMock<ChooseAccountAccessUiProps>()
    }
  }

  test("RecoveringKeyboxData") {
    accountDataStateMachine.emitModel(
      NoActiveAccountData.RecoveringAccountData(
        debugOptions = DebugOptions(
          bitcoinNetworkType = SIGNET,
          isHardwareFake = true,
          f8eEnvironment = Development,
          isUsingSocRecFakes = true,
          isTestAccount = true
        ),
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
      awaitScreenWithBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitScreenWithBodyModelMock<LostAppRecoveryUiProps>()
    }
  }

  test("RecoveringLiteAccountData") {
    accountDataStateMachine.emitModel(
      NoActiveAccountData.RecoveringLiteAccountData(
        cloudBackup = CloudBackupV2WithLiteAccountMock,
        onAccountCreated = {},
        onExit = {}
      )
    )
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitScreenWithBodyModelMock<LiteAccountCloudBackupRestorationUiProps>()
    }
  }

  test("RecoveringAccountWithEmergencyAccessKit") {
    accountDataStateMachine.emitModel(
      RecoveringAccountWithEmergencyAccessKit(
        onExit = {}
      )
    )
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitScreenWithBodyModelMock<EmergencyAccessKitRecoveryUiStateMachineProps>()
    }
  }

  test("NoLongerRecoveringKeyboxData") {
    accountDataStateMachine.emitModel(AccountData.NoLongerRecoveringFullAccountData(App))
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitScreenWithBodyModelMock<NoLongerRecoveringUiProps>()
    }
  }

  test("SomeoneElseIsRecoveringKeyboxData") {
    accountDataStateMachine.emitModel(
      AccountData.SomeoneElseIsRecoveringFullAccountData(
        data = SomeoneElseIsRecoveringData.ShowingSomeoneElseIsRecoveringData(App, {}),
        fullAccountConfig = FullAccountConfigMock,
        fullAccountId = FullAccountIdMock
      )
    )
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitScreenWithBodyModelMock<SomeoneElseIsRecoveringUiProps>()
    }
  }

  test("existing software account") {
    loadAppService.appState.value = null

    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = HasActiveSoftwareAccount(SoftwareAccountMock)

      awaitScreenWithBodyModelMock<HomeUiProps> {
        account.shouldBe(SoftwareAccountMock)
      }
    }
  }

  test("create a new software account") {
    loadAppService.appState.value = null

    accountDataStateMachine.emitModel(gettingStartedData)
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      expectNoEvents()

      loadAppService.appState.value = AppState.Undetermined

      awaitScreenWithBodyModelMock<ChooseAccountAccessUiProps> {
        onSoftwareWalletCreated(SoftwareAccountMock)
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        message.shouldBe("Welcome to Bitkey")
      }

      awaitScreenWithBodyModelMock<HomeUiProps> {
        account.shouldBe(SoftwareAccountMock)
      }
    }
  }

  test("ActiveKeyboxLoadedData w/ biometric auth required") {
    biometricAuthService.isBiometricAuthRequiredFlow.value = true

    accountDataStateMachine.emitModel(ActiveKeyboxLoadedDataMock)
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
      awaitScreenWithBodyModelMock<BiometricPromptProps>()
    }
  }
})
