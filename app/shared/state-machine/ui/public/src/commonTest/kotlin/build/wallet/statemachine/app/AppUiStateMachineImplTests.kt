package build.wallet.statemachine.app

import build.wallet.account.analytics.AppInstallationMock
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_APP_SCREEN_IMPRESSION
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.keybox.KeyboxConfigMock
import build.wallet.cloud.backup.CloudBackupV2WithLiteAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.emergencyaccesskit.EakDataFake
import build.wallet.f8e.debug.NetworkingDebugConfigRepositoryFake
import build.wallet.money.display.CurrencyPreferenceDataMock
import build.wallet.platform.config.AppVariant
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.account.ChooseAccountAccessUiProps
import build.wallet.statemachine.account.ChooseAccountAccessUiStateMachine
import build.wallet.statemachine.account.create.full.CreateAccountUiProps
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachine
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiProps
import build.wallet.statemachine.account.create.lite.CreateLiteAccountUiStateMachine
import build.wallet.statemachine.core.SplashBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.account.create.LoadedOnboardConfigDataMock
import build.wallet.statemachine.data.app.AppData
import build.wallet.statemachine.data.app.AppData.AppLoadedData
import build.wallet.statemachine.data.app.AppData.LoadingAppData
import build.wallet.statemachine.data.app.AppDataStateMachine
import build.wallet.statemachine.data.firmware.FirmwareDataUpToDateMock
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.GettingStartedData
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.data.keybox.OnboardingKeyboxDataMock
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigData.LoadedTemplateKeyboxConfigData
import build.wallet.statemachine.data.lightning.LightningNodeData.LightningNodeDisabledData
import build.wallet.statemachine.data.recovery.conflict.NoLongerRecoveringData
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData
import build.wallet.statemachine.data.sync.PlaceholderElectrumServerDataMock
import build.wallet.statemachine.dev.DebugMenuProps
import build.wallet.statemachine.dev.DebugMenuStateMachine
import build.wallet.statemachine.home.full.HomeUiProps
import build.wallet.statemachine.home.full.HomeUiStateMachine
import build.wallet.statemachine.home.lite.LiteHomeUiProps
import build.wallet.statemachine.home.lite.LiteHomeUiStateMachine
import build.wallet.statemachine.recovery.cloud.LiteAccountCloudBackupRestorationUiProps
import build.wallet.statemachine.recovery.cloud.LiteAccountCloudBackupRestorationUiStateMachine
import build.wallet.statemachine.recovery.conflict.NoLongerRecoveringUiProps
import build.wallet.statemachine.recovery.conflict.NoLongerRecoveringUiStateMachine
import build.wallet.statemachine.recovery.conflict.SomeoneElseIsRecoveringUiProps
import build.wallet.statemachine.recovery.conflict.SomeoneElseIsRecoveringUiStateMachine
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitRecoveryUiStateMachine
import build.wallet.statemachine.recovery.emergencyaccesskit.EmergencyAccessKitRecoveryUiStateMachineProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiProps
import build.wallet.statemachine.recovery.lostapp.LostAppRecoveryUiStateMachine
import build.wallet.statemachine.root.AppUiStateMachineImpl
import build.wallet.statemachine.start.GettingStartedRoutingProps
import build.wallet.statemachine.start.GettingStartedRoutingStateMachine
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
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
  val appDataStateMachine =
    object : AppDataStateMachine, StateMachineMock<Unit, AppData>(
      initialModel = LoadingAppData
    ) {}
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
  lateinit var stateMachine: AppUiStateMachineImpl
  val testScope = TestScope()

  // Fakes are stateful, need to reinitialize before each test to reset the state.
  beforeTest {
    appDataStateMachine.reset()
    stateMachine =
      AppUiStateMachineImpl(
        appVariant = AppVariant.Development,
        debugMenuStateMachine =
          object : DebugMenuStateMachine, ScreenStateMachineMock<DebugMenuProps>(
            id = "debug-menu"
          ) {},
        eventTracker = eventTracker,
        lostAppRecoveryUiStateMachine = recoveringKeyboxUiStateMachine,
        homeUiStateMachine = object : HomeUiStateMachine, ScreenStateMachineMock<HomeUiProps>(id = "home") {},
        liteHomeUiStateMachine = object : LiteHomeUiStateMachine, ScreenStateMachineMock<LiteHomeUiProps>(id = "lite-home") {},
        chooseAccountAccessUiStateMachine = object : ChooseAccountAccessUiStateMachine, ScreenStateMachineMock<ChooseAccountAccessUiProps>(id = "account-access") {},
        createAccountUiStateMachine = createAccountUiStateMachine,
        appDataStateMachine = appDataStateMachine,
        noLongerRecoveringUiStateMachine = noLongerRecoveringUiStateMachine,
        someoneElseIsRecoveringUiStateMachine = someoneElseIsRecoveringUiStateMachine,
        gettingStartedRoutingStateMachine = gettingStartedRoutingStateMachine,
        createLiteAccountUiStateMachine = createLiteAccountUiStateMachine,
        liteAccountCloudBackupRestorationUiStateMachine =
        liteAccountCloudBackupRestorationUiStateMachine,
        emergencyAccessKitRecoveryUiStateMachine = emergencyAccessKitRecoveryUiStateMachine,
        networkingDebugConfigRepository = NetworkingDebugConfigRepositoryFake(),
        appCoroutineScope = testScope
      )
  }

  suspend fun EventTrackerMock.awaitSplashScreenEvent() {
    eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_SCREEN_IMPRESSION, GeneralEventTrackerScreenId.SPLASH_SCREEN)
    )
  }

  test("LoadingAppData") {
    appDataStateMachine.emitModel(LoadingAppData)
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      eventTracker.awaitSplashScreenEvent()
    }
  }

  test("AppLoadedData - ActiveKeyboxLoadedData") {
    appDataStateMachine.emitModel(
      AppLoadedData(
        appInstallation = AppInstallationMock,
        accountData = ActiveKeyboxLoadedDataMock,
        lightningNodeData = LightningNodeDisabledData,
        electrumServerData = PlaceholderElectrumServerDataMock,
        firmwareData = FirmwareDataUpToDateMock,
        currencyPreferenceData = CurrencyPreferenceDataMock,
        eakAssociation = EakDataFake
      )
    )
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      awaitScreenWithBodyModelMock<HomeUiProps> {
        accountData.shouldBe(ActiveKeyboxLoadedDataMock)
      }
    }
  }

  test("AppLoadedData - CreatingAccountData") {
    appDataStateMachine.emitModel(
      AppLoadedData(
        appInstallation = AppInstallationMock,
        accountData =
          AccountData.NoActiveAccountData.CreatingFullAccountData(
            createFullAccountData = OnboardingKeyboxDataMock(),
            templateKeyboxConfig = KeyboxConfigMock
          ),
        lightningNodeData = LightningNodeDisabledData,
        electrumServerData = PlaceholderElectrumServerDataMock,
        firmwareData = FirmwareDataUpToDateMock,
        currencyPreferenceData = CurrencyPreferenceDataMock,
        eakAssociation = EakDataFake
      )
    )
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      awaitScreenWithBodyModelMock<CreateAccountUiProps>()
    }
  }

  test("AppLoadedData - ReadyToChooseAccountAccessKeyboxData") {
    appDataStateMachine.emitModel(
      AppLoadedData(
        appInstallation = AppInstallationMock,
        accountData =
          GettingStartedData(
            startFullAccountCreation = {},
            startLiteAccountCreation = {},
            startRecovery = {},
            startEmergencyAccessRecovery = {},
            newAccountOnboardConfigData = LoadedOnboardConfigDataMock,
            templateKeyboxConfigData =
              LoadedTemplateKeyboxConfigData(
                config = KeyboxConfigMock,
                updateConfig = {}
              ),
            isNavigatingBack = false
          ),
        lightningNodeData = LightningNodeDisabledData,
        electrumServerData = PlaceholderElectrumServerDataMock,
        firmwareData = FirmwareDataUpToDateMock,
        currencyPreferenceData = CurrencyPreferenceDataMock,
        eakAssociation = EakDataFake
      )
    )
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      awaitScreenWithBodyModelMock<ChooseAccountAccessUiProps>()
    }
  }

  test("AppLoadedData - RecoveringKeyboxData") {
    appDataStateMachine.emitModel(
      AppLoadedData(
        appInstallation = AppInstallationMock,
        accountData =
          AccountData.NoActiveAccountData.RecoveringAccountData(
            templateKeyboxConfig = KeyboxConfigMock,
            lostAppRecoveryData =
              LostAppRecoveryData.LostAppRecoveryInProgressData(
                recoveryInProgressData =
                  RecoveryInProgressData.WaitingForRecoveryDelayPeriodData(
                    factorToRecover = PhysicalFactor.Hardware,
                    delayPeriodStartTime = Instant.DISTANT_PAST,
                    delayPeriodEndTime = Instant.DISTANT_PAST,
                    cancel = {},
                    retryCloudRecovery = null
                  )
              )
          ),
        lightningNodeData = LightningNodeDisabledData,
        electrumServerData = PlaceholderElectrumServerDataMock,
        firmwareData = FirmwareDataUpToDateMock,
        currencyPreferenceData = CurrencyPreferenceDataMock,
        eakAssociation = EakDataFake
      )
    )
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      awaitScreenWithBodyModelMock<LostAppRecoveryUiProps>()
    }
  }

  test("AppLoadedData - RecoveringLiteAccountData") {
    appDataStateMachine.emitModel(
      AppLoadedData(
        appInstallation = AppInstallationMock,
        accountData =
          AccountData.NoActiveAccountData.RecoveringLiteAccountData(
            cloudBackup = CloudBackupV2WithLiteAccountMock,
            onAccountCreated = {},
            onExit = {}
          ),
        lightningNodeData = LightningNodeDisabledData,
        electrumServerData = PlaceholderElectrumServerDataMock,
        firmwareData = FirmwareDataUpToDateMock,
        currencyPreferenceData = CurrencyPreferenceDataMock,
        eakAssociation = EakDataFake
      )
    )
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      awaitScreenWithBodyModelMock<LiteAccountCloudBackupRestorationUiProps>()
    }
  }

  test("AppLoadedData - RecoveringAccountWithEmergencyAccessKit") {
    appDataStateMachine.emitModel(
      AppLoadedData(
        appInstallation = AppInstallationMock,
        accountData =
          AccountData.NoActiveAccountData.RecoveringAccountWithEmergencyAccessKit(
            templateKeyboxConfig = KeyboxConfigMock,
            onExit = {}
          ),
        lightningNodeData = LightningNodeDisabledData,
        electrumServerData = PlaceholderElectrumServerDataMock,
        firmwareData = FirmwareDataUpToDateMock,
        currencyPreferenceData = CurrencyPreferenceDataMock,
        eakAssociation = EakDataFake
      )
    )
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      awaitScreenWithBodyModelMock<EmergencyAccessKitRecoveryUiStateMachineProps>()
    }
  }

  test("AppLoadedData - NoLongerRecoveringKeyboxData") {
    appDataStateMachine.emitModel(
      AppLoadedData(
        appInstallation = AppInstallationMock,
        accountData =
          AccountData.NoLongerRecoveringFullAccountData(
            data = NoLongerRecoveringData.ShowingNoLongerRecoveringData(App, {})
          ),
        lightningNodeData = LightningNodeDisabledData,
        electrumServerData = PlaceholderElectrumServerDataMock,
        firmwareData = FirmwareDataUpToDateMock,
        currencyPreferenceData = CurrencyPreferenceDataMock,
        eakAssociation = EakDataFake
      )
    )
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      awaitScreenWithBodyModelMock<NoLongerRecoveringUiProps>()
    }
  }

  test("AppLoadedData - SomeoneElseIsRecoveringKeyboxData") {
    appDataStateMachine.emitModel(
      AppLoadedData(
        appInstallation = AppInstallationMock,
        accountData =
          AccountData.SomeoneElseIsRecoveringFullAccountData(
            data = SomeoneElseIsRecoveringData.ShowingSomeoneElseIsRecoveringData(App, {}),
            keyboxConfig = KeyboxConfigMock,
            fullAccountId = FullAccountIdMock
          ),
        lightningNodeData = LightningNodeDisabledData,
        electrumServerData = PlaceholderElectrumServerDataMock,
        firmwareData = FirmwareDataUpToDateMock,
        currencyPreferenceData = CurrencyPreferenceDataMock,
        eakAssociation = EakDataFake
      )
    )
    stateMachine.test(Unit) {
      awaitScreenWithBody<SplashBodyModel>()
      awaitScreenWithBodyModelMock<SomeoneElseIsRecoveringUiProps>()
    }
  }
})
