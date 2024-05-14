package build.wallet.statemachine.data.app

import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.configuration.FiatMobilePayConfigurationRepositoryMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagInitializerMock
import build.wallet.feature.FeatureFlagSyncerMock
import build.wallet.money.currency.FiatCurrencyRepositoryMock
import build.wallet.platform.permissions.PermissionCheckerMock
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
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigData
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigData.LoadedTemplateFullAccountConfigData
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigDataStateMachine
import build.wallet.statemachine.data.lightning.LightningNodeData
import build.wallet.statemachine.data.lightning.LightningNodeData.LightningNodeDisabledData
import build.wallet.statemachine.data.lightning.LightningNodeDataStateMachine
import build.wallet.statemachine.data.sync.ElectrumServerData
import build.wallet.statemachine.data.sync.ElectrumServerDataProps
import build.wallet.statemachine.data.sync.ElectrumServerDataStateMachine
import build.wallet.statemachine.data.sync.PlaceholderElectrumServerDataMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AppDataStateMachineImplTests : FunSpec({

  val featureFlagInitializer = FeatureFlagInitializerMock(turbines::create)
  val featureFlagSyncer = FeatureFlagSyncerMock(turbines::create)
  val accountDataStateMachine =
    object : AccountDataStateMachine, StateMachineMock<AccountDataProps, AccountData>(
      initialModel = CheckingActiveAccountData
    ) {}
  val lightningNodeDataStateMachine =
    object : LightningNodeDataStateMachine,
      StateMachineMock<Unit, LightningNodeData>(initialModel = LightningNodeDisabledData) {}
  val permissionChecker = PermissionCheckerMock()
  val templateFullAccountConfigDataStateMachine =
    object : TemplateFullAccountConfigDataStateMachine,
      StateMachineMock<Unit, TemplateFullAccountConfigData>(
        initialModel = LoadedTemplateFullAccountConfigData(
          config = FullAccountConfigMock,
          updateConfig = {
          }
        )
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
  val fiatCurrencyRepository = FiatCurrencyRepositoryMock(turbines::create)
  val fiatMobilePayConfigurationRepository =
    FiatMobilePayConfigurationRepositoryMock(turbines::create)

  val stateMachine =
    AppDataStateMachineImpl(
      featureFlagInitializer = featureFlagInitializer,
      featureFlagSyncer = featureFlagSyncer,
      accountDataStateMachine = accountDataStateMachine,
      lightningNodeDataStateMachine = lightningNodeDataStateMachine,
      templateFullAccountConfigDataStateMachine = templateFullAccountConfigDataStateMachine,
      electrumServerDataStateMachine = electrumServerDataStateMachine,
      firmwareDataStateMachine = firmwareDataStateMachine,
      fiatCurrencyRepository = fiatCurrencyRepository,
      fiatMobilePayConfigurationRepository = fiatMobilePayConfigurationRepository
    )

  suspend fun shouldInitializeFeatureFlags() {
    featureFlagInitializer.initializeFeatureFlagsCalls.awaitItem().shouldBe(Unit)
    featureFlagSyncer.initializeSyncLoopCalls.awaitItem().shouldBe(Unit)
    featureFlagSyncer.syncFeatureFlagsCalls.awaitItem().shouldBe(Unit)
  }

  suspend fun shouldLaunchRepositories() {
    fiatCurrencyRepository.updateFromServerCalls.awaitItem().shouldBe(Unit)
    fiatMobilePayConfigurationRepository.launchSyncAndUpdateFromServerCalls.awaitItem()
  }

  suspend fun shouldRunInitialSideEffects() {
    shouldInitializeFeatureFlags()
    shouldLaunchRepositories()
  }

  val accountData = ActiveKeyboxLoadedDataMock

  test("load app") {
    permissionChecker.permissionsOn = true

    stateMachine.test(props = Unit) {
      // Initial app loading data
      awaitItem().shouldBe(LoadingAppData)

      shouldRunInitialSideEffects()

      // App data updated, loading keybox
      awaitItem().shouldBe(
        AppLoadedData(
          accountData = CheckingActiveAccountData,
          lightningNodeData = LightningNodeDisabledData,
          electrumServerData = PlaceholderElectrumServerDataMock,
          firmwareData = FirmwareDataUpToDateMock
        )
      )

      // Update child keybox data state machine
      accountDataStateMachine.emitModel(accountData)

      // App data updated, keybox loaded
      awaitItem().shouldBe(
        AppLoadedData(
          accountData = accountData,
          lightningNodeData = LightningNodeDisabledData,
          electrumServerData = PlaceholderElectrumServerDataMock,
          firmwareData = FirmwareDataUpToDateMock
        )
      )
    }
  }
})
