package build.wallet.statemachine.data.app

import build.wallet.account.analytics.AppInstallationDaoMock
import build.wallet.account.analytics.AppInstallationMock
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_OPEN_INITIALIZE
import build.wallet.bitkey.keybox.KeyboxConfigMock
import build.wallet.configuration.FiatMobilePayConfigurationRepositoryMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.emergencyaccesskit.EakDataFake
import build.wallet.emergencyaccesskit.EmergencyAccessKitDataProviderFake
import build.wallet.f8e.debug.NetworkingDebugConfigRepositoryFake
import build.wallet.feature.FeatureFlagInitializerMock
import build.wallet.money.currency.FiatCurrencyRepositoryMock
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryMock
import build.wallet.money.display.CurrencyPreferenceData
import build.wallet.money.display.CurrencyPreferenceDataMock
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.platform.permissions.PermissionCheckerMock
import build.wallet.queueprocessor.PeriodicProcessorMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.app.AppData.AppLoadedData
import build.wallet.statemachine.data.app.AppData.LoadingAppData
import build.wallet.statemachine.data.firmware.FirmwareData
import build.wallet.statemachine.data.firmware.FirmwareDataProps
import build.wallet.statemachine.data.firmware.FirmwareDataStateMachine
import build.wallet.statemachine.data.firmware.FirmwareDataUpToDateMock
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.statemachine.data.keybox.AccountData.CheckingActiveAccountData
import build.wallet.statemachine.data.keybox.AccountDataProps
import build.wallet.statemachine.data.keybox.AccountDataStateMachine
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigData
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigData.LoadedTemplateKeyboxConfigData
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigDataStateMachine
import build.wallet.statemachine.data.lightning.LightningNodeData
import build.wallet.statemachine.data.lightning.LightningNodeData.LightningNodeDisabledData
import build.wallet.statemachine.data.lightning.LightningNodeDataStateMachine
import build.wallet.statemachine.data.money.currency.CurrencyPreferenceDataStateMachine
import build.wallet.statemachine.data.sync.ElectrumServerData
import build.wallet.statemachine.data.sync.ElectrumServerDataProps
import build.wallet.statemachine.data.sync.ElectrumServerDataStateMachine
import build.wallet.statemachine.data.sync.PlaceholderElectrumServerDataMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AppDataStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)
  val periodicEventSender = PeriodicProcessorMock("periodicEventSender", turbines::create)
  val periodicFirmwareTelemetrySender =
    PeriodicProcessorMock("periodicFirmwareTelemetrySender", turbines::create)
  val periodicFirmwareCoredumpSender =
    PeriodicProcessorMock("periodicFirmwareCoredumpSender", turbines::create)
  val periodicRegisterWatchAddressSender =
    PeriodicProcessorMock("periodicRegisterWatchAddressSender", turbines::create)
  val appInstallationDao = AppInstallationDaoMock()
  val featureFlagInitializer = FeatureFlagInitializerMock(turbines::create)
  val accountDataStateMachine =
    object : AccountDataStateMachine, StateMachineMock<AccountDataProps, AccountData>(
      initialModel = CheckingActiveAccountData
    ) {}
  val lightningNodeDataStateMachine =
    object : LightningNodeDataStateMachine,
      StateMachineMock<Unit, LightningNodeData>(initialModel = LightningNodeDisabledData) {}
  val permissionChecker = PermissionCheckerMock()
  val templateKeyboxConfigDataStateMachine =
    object : TemplateKeyboxConfigDataStateMachine,
      StateMachineMock<Unit, TemplateKeyboxConfigData>(
        initialModel = LoadedTemplateKeyboxConfigData(config = KeyboxConfigMock, updateConfig = {})
      ) {}
  val electrumServerDataStateMachine =
    object : ElectrumServerDataStateMachine,
      StateMachineMock<ElectrumServerDataProps, ElectrumServerData>(
        initialModel = PlaceholderElectrumServerDataMock
      ) {}
  val firmwareDataStateMachine =
    object : FirmwareDataStateMachine, StateMachineMock<FirmwareDataProps, FirmwareData>(
      FirmwareDataUpToDateMock
    ) {}
  val currencyPreferenceDataStateMachine =
    object : CurrencyPreferenceDataStateMachine,
      StateMachineMock<Unit, CurrencyPreferenceData>(CurrencyPreferenceDataMock) {}
  val networkingDebugConfigRepository = NetworkingDebugConfigRepositoryFake()
  val bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryMock(turbines::create)
  val fiatCurrencyRepository = FiatCurrencyRepositoryMock(turbines::create)
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val fiatMobilePayConfigurationRepository =
    FiatMobilePayConfigurationRepositoryMock(turbines::create)

  val emergencyAccessKitDataProviderFake = EmergencyAccessKitDataProviderFake(EakDataFake)

  val stateMachine =
    AppDataStateMachineImpl(
      eventTracker = eventTracker,
      appInstallationDao = appInstallationDao,
      featureFlagInitializer = featureFlagInitializer,
      accountDataStateMachine = accountDataStateMachine,
      periodicEventProcessor = periodicEventSender,
      periodicFirmwareTelemetryProcessor = periodicFirmwareTelemetrySender,
      periodicFirmwareCoredumpProcessor = periodicFirmwareCoredumpSender,
      periodicRegisterWatchAddressProcessor = periodicRegisterWatchAddressSender,
      lightningNodeDataStateMachine = lightningNodeDataStateMachine,
      templateKeyboxConfigDataStateMachine = templateKeyboxConfigDataStateMachine,
      electrumServerDataStateMachine = electrumServerDataStateMachine,
      firmwareDataStateMachine = firmwareDataStateMachine,
      currencyPreferenceDataStateMachine = currencyPreferenceDataStateMachine,
      networkingDebugConfigRepository = networkingDebugConfigRepository,
      bitcoinDisplayPreferenceRepository = bitcoinDisplayPreferenceRepository,
      fiatCurrencyRepository = fiatCurrencyRepository,
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
      fiatMobilePayConfigurationRepository = fiatMobilePayConfigurationRepository,
      emergencyAccessKitDataProvider = emergencyAccessKitDataProviderFake
    )

  beforeTest {
    appInstallationDao.reset()
  }

  suspend fun shouldStartPeriodicEventSender() {
    periodicEventSender.startCalls.awaitItem().shouldBe(Unit)
  }

  suspend fun shouldStartPeriodicFirmwareTelemetrySender() {
    periodicFirmwareTelemetrySender.startCalls.awaitItem().shouldBe(Unit)
  }

  suspend fun shouldStartPeriodicFirmwareCoredumpSender() {
    periodicFirmwareCoredumpSender.startCalls.awaitItem().shouldBe(Unit)
  }

  suspend fun shouldStartPeriodicRegisterWatchAddressSender() {
    periodicRegisterWatchAddressSender.startCalls.awaitItem().shouldBe(Unit)
  }

  suspend fun shouldTrackAppOpenEvent() {
    eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_OPEN_INITIALIZE))
  }

  suspend fun shouldInitializeFeatureFlags() {
    featureFlagInitializer.initializeFeatureFlagsCalls.awaitItem().shouldBe(Unit)
  }

  suspend fun shouldLaunchRepositories() {
    bitcoinDisplayPreferenceRepository.launchSyncCalls?.awaitItem()
    fiatCurrencyPreferenceRepository.launchSyncCalls.awaitItem()
    fiatCurrencyRepository.launchSyncAndUpdateFromServerCalls.awaitItem().shouldBe(Unit)
    fiatMobilePayConfigurationRepository.launchSyncAndUpdateFromServerCalls.awaitItem()
  }

  suspend fun shouldRunInitialSideEffects() {
    shouldStartPeriodicEventSender()
    shouldStartPeriodicFirmwareTelemetrySender()
    shouldStartPeriodicFirmwareCoredumpSender()
    shouldStartPeriodicRegisterWatchAddressSender()
    shouldTrackAppOpenEvent()
    shouldInitializeFeatureFlags()
    shouldLaunchRepositories()
  }

  val accountData = ActiveKeyboxLoadedDataMock

  test("load app") {
    appInstallationDao.appInstallation = AppInstallationMock
    permissionChecker.permissionsOn = true

    stateMachine.test(props = Unit) {
      // Initial app loading data
      awaitItem().shouldBe(LoadingAppData)

      shouldRunInitialSideEffects()

      // App data updated, loading keybox
      awaitItem().shouldBe(
        AppLoadedData(
          appInstallation = AppInstallationMock,
          accountData = CheckingActiveAccountData,
          lightningNodeData = LightningNodeDisabledData,
          electrumServerData = PlaceholderElectrumServerDataMock,
          firmwareData = FirmwareDataUpToDateMock,
          currencyPreferenceData = CurrencyPreferenceDataMock,
          eakAssociation = EakDataFake
        )
      )

      // Update child keybox data state machine
      accountDataStateMachine.emitModel(accountData)

      // App data updated, keybox loaded
      awaitItem().shouldBe(
        AppLoadedData(
          appInstallation = AppInstallationMock,
          accountData = accountData,
          lightningNodeData = LightningNodeDisabledData,
          electrumServerData = PlaceholderElectrumServerDataMock,
          firmwareData = FirmwareDataUpToDateMock,
          currencyPreferenceData = CurrencyPreferenceDataMock,
          eakAssociation = EakDataFake
        )
      )
    }
  }
})
