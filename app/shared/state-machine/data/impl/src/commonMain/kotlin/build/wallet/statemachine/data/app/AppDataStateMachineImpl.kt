package build.wallet.statemachine.data.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.account.analytics.AppInstallation
import build.wallet.account.analytics.AppInstallationDao
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_OPEN_INITIALIZE
import build.wallet.configuration.FiatMobilePayConfigurationRepository
import build.wallet.emergencyaccesskit.EmergencyAccessKitAssociation
import build.wallet.emergencyaccesskit.EmergencyAccessKitDataProvider
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.debug.NetworkingDebugConfigRepository
import build.wallet.feature.FeatureFlagInitializer
import build.wallet.money.currency.FiatCurrencyRepository
import build.wallet.money.display.BitcoinDisplayPreferenceRepository
import build.wallet.money.display.CurrencyPreferenceData
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.queueprocessor.PeriodicProcessor
import build.wallet.statemachine.data.app.AppData.LoadingAppData
import build.wallet.statemachine.data.app.AppDataStateMachineImpl.AppLoadBlockingEffectState.COMPLETE
import build.wallet.statemachine.data.app.AppDataStateMachineImpl.AppLoadBlockingEffectState.IN_PROGRESS
import build.wallet.statemachine.data.firmware.FirmwareData
import build.wallet.statemachine.data.firmware.FirmwareDataProps
import build.wallet.statemachine.data.firmware.FirmwareDataStateMachine
import build.wallet.statemachine.data.keybox.AccountDataProps
import build.wallet.statemachine.data.keybox.AccountDataStateMachine
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigData.LoadedTemplateKeyboxConfigData
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigData.LoadingTemplateKeyboxConfigData
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigDataStateMachine
import build.wallet.statemachine.data.lightning.LightningNodeData
import build.wallet.statemachine.data.lightning.LightningNodeDataStateMachine
import build.wallet.statemachine.data.money.currency.CurrencyPreferenceDataStateMachine
import build.wallet.statemachine.data.sync.ElectrumServerData
import build.wallet.statemachine.data.sync.ElectrumServerDataProps
import build.wallet.statemachine.data.sync.ElectrumServerDataStateMachine
import com.github.michaelbull.result.get

class AppDataStateMachineImpl(
  private val eventTracker: EventTracker,
  private val appInstallationDao: AppInstallationDao,
  private val featureFlagInitializer: FeatureFlagInitializer,
  private val accountDataStateMachine: AccountDataStateMachine,
  private val periodicEventProcessor: PeriodicProcessor,
  private val periodicFirmwareTelemetryProcessor: PeriodicProcessor,
  private val periodicFirmwareCoredumpProcessor: PeriodicProcessor,
  private val periodicRegisterWatchAddressProcessor: PeriodicProcessor,
  private val lightningNodeDataStateMachine: LightningNodeDataStateMachine,
  private val templateKeyboxConfigDataStateMachine: TemplateKeyboxConfigDataStateMachine,
  private val electrumServerDataStateMachine: ElectrumServerDataStateMachine,
  private val firmwareDataStateMachine: FirmwareDataStateMachine,
  private val currencyPreferenceDataStateMachine: CurrencyPreferenceDataStateMachine,
  private val networkingDebugConfigRepository: NetworkingDebugConfigRepository,
  private val bitcoinDisplayPreferenceRepository: BitcoinDisplayPreferenceRepository,
  private val fiatCurrencyRepository: FiatCurrencyRepository,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val fiatMobilePayConfigurationRepository: FiatMobilePayConfigurationRepository,
  private val emergencyAccessKitDataProvider: EmergencyAccessKitDataProvider,
) : AppDataStateMachine {
  enum class AppLoadBlockingEffectState {
    IN_PROGRESS,
    COMPLETE,
  }

  @Composable
  override fun model(props: Unit): AppData {
    var initializeFeatureFlagsEffectState by remember { mutableStateOf(IN_PROGRESS) }
    InitializeFeatureFlagsEffect {
      initializeFeatureFlagsEffectState = COMPLETE
    }

    LogAppOpenedEventEffect()
    SyncAnalyticsEventsEffect()
    SyncFirmwareTelemetryEffect()
    SyncFirmwareCoredumpEffect()
    RetryRegisterWatchAddressEffect()
    SyncRepositoriesEffect()

    val lightningNodeData = lightningNodeDataStateMachine.model(Unit)

    val templateKeyboxConfigData = templateKeyboxConfigDataStateMachine.model(Unit)

    val appData: AppData =
      when (templateKeyboxConfigData) {
        LoadingTemplateKeyboxConfigData -> LoadingAppData
        is LoadedTemplateKeyboxConfigData -> {
          SyncServerBasedRepositoriesEffect(
            f8eEnvironment = templateKeyboxConfigData.config.f8eEnvironment
          )
          val blockingEffectsState = listOf(initializeFeatureFlagsEffectState)
          val allBlockingEffectsAreComplete = blockingEffectsState.all { it == COMPLETE }

          val electrumServerData =
            electrumServerDataStateMachine.model(
              ElectrumServerDataProps(
                f8eEnvironment = templateKeyboxConfigData.config.f8eEnvironment,
                network = templateKeyboxConfigData.config.networkType
              )
            )

          val firmwareData =
            firmwareDataStateMachine.model(
              props =
                FirmwareDataProps(
                  isHardwareFake = templateKeyboxConfigData.config.isHardwareFake
                )
            )

          val fiatCurrencyPreferenceData =
            currencyPreferenceDataStateMachine.model(Unit)

          val eakAssociation = emergencyAccessKitDataProvider.getAssociatedEakData()

          when (val appInstallation = appInstallation()) {
            null -> LoadingAppData
            else ->
              when (allBlockingEffectsAreComplete) {
                true ->
                  AppLoadedData(
                    appInstallation,
                    lightningNodeData,
                    templateKeyboxConfigData,
                    electrumServerData,
                    firmwareData,
                    fiatCurrencyPreferenceData,
                    eakAssociation
                  )

                false -> LoadingAppData
              }
          }
        }
      }

    return appData
  }

  @Composable
  private fun SyncAnalyticsEventsEffect() {
    LaunchedEffect("sync-analytics-events") {
      periodicEventProcessor.start()
    }
  }

  @Composable
  private fun SyncFirmwareTelemetryEffect() {
    LaunchedEffect("firmware-telemetry-upload") {
      periodicFirmwareTelemetryProcessor.start()
    }
  }

  @Composable
  private fun SyncFirmwareCoredumpEffect() {
    LaunchedEffect("firmware-coredump-upload") {
      periodicFirmwareCoredumpProcessor.start()
    }
  }

  @Composable
  private fun RetryRegisterWatchAddressEffect() {
    LaunchedEffect("retry-register-watch-address") {
      periodicRegisterWatchAddressProcessor.start()
    }
  }

  @Composable
  private fun SyncRepositoriesEffect() {
    LaunchedEffect("sync-repositories") {
      bitcoinDisplayPreferenceRepository.launchSync(scope = this)
      fiatCurrencyPreferenceRepository.launchSync(scope = this)
      networkingDebugConfigRepository.launchSync(scope = this)
    }
  }

  @Composable
  private fun SyncServerBasedRepositoriesEffect(f8eEnvironment: F8eEnvironment) {
    LaunchedEffect("sync-server-based-repository", f8eEnvironment) {
      fiatCurrencyRepository.launchSyncAndUpdateFromServer(
        scope = this,
        f8eEnvironment = f8eEnvironment
      )
      fiatMobilePayConfigurationRepository.launchSyncAndUpdateFromServer(
        scope = this,
        f8eEnvironment = f8eEnvironment
      )
    }
  }

  @Composable
  private fun LogAppOpenedEventEffect() {
    LaunchedEffect("app-opened") {
      eventTracker.track(action = ACTION_APP_OPEN_INITIALIZE)
    }
  }

  @Composable
  private fun InitializeFeatureFlagsEffect(onComplete: () -> Unit) {
    LaunchedEffect("initialize-feature-flags") {
      featureFlagInitializer.initializeAllFlags()
      onComplete()
    }
  }

  @Composable
  private fun appInstallation(): AppInstallation? {
    return produceState<AppInstallation?>(initialValue = null) {
      value = appInstallationDao.getOrCreateAppInstallation().get()
    }.value
  }

  @Composable
  private fun AppLoadedData(
    appInstallation: AppInstallation,
    lightningNodeData: LightningNodeData,
    templateKeyboxConfigData: LoadedTemplateKeyboxConfigData,
    electrumServerData: ElectrumServerData,
    firmwareData: FirmwareData,
    currencyPreferenceData: CurrencyPreferenceData,
    eakAssociation: EmergencyAccessKitAssociation,
  ): AppData {
    val accountData =
      accountDataStateMachine.model(
        props = AccountDataProps(templateKeyboxConfigData, currencyPreferenceData)
      )
    return AppData.AppLoadedData(
      appInstallation = appInstallation,
      lightningNodeData = lightningNodeData,
      accountData = accountData,
      electrumServerData = electrumServerData,
      firmwareData = firmwareData,
      currencyPreferenceData = currencyPreferenceData,
      eakAssociation = eakAssociation
    )
  }
}
