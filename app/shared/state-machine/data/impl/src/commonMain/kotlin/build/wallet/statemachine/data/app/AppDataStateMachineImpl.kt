package build.wallet.statemachine.data.app

import androidx.compose.runtime.*
import build.wallet.f8e.F8eEnvironment
import build.wallet.feature.FeatureFlagInitializer
import build.wallet.feature.FeatureFlagSyncer
import build.wallet.money.currency.FiatCurrencyRepository
import build.wallet.statemachine.data.app.AppData.LoadingAppData
import build.wallet.statemachine.data.app.AppDataStateMachineImpl.AppLoadBlockingEffectState.COMPLETE
import build.wallet.statemachine.data.app.AppDataStateMachineImpl.AppLoadBlockingEffectState.IN_PROGRESS
import build.wallet.statemachine.data.firmware.FirmwareData
import build.wallet.statemachine.data.firmware.FirmwareDataProps
import build.wallet.statemachine.data.firmware.FirmwareDataStateMachine
import build.wallet.statemachine.data.keybox.AccountDataProps
import build.wallet.statemachine.data.keybox.AccountDataStateMachine
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigData.LoadedTemplateFullAccountConfigData
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigData.LoadingTemplateFullAccountConfigData
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigDataStateMachine
import build.wallet.statemachine.data.sync.ElectrumServerData
import build.wallet.statemachine.data.sync.ElectrumServerDataProps
import build.wallet.statemachine.data.sync.ElectrumServerDataStateMachine
import kotlinx.coroutines.launch

class AppDataStateMachineImpl(
  private val featureFlagInitializer: FeatureFlagInitializer,
  private val featureFlagSyncer: FeatureFlagSyncer,
  private val accountDataStateMachine: AccountDataStateMachine,
  private val templateFullAccountConfigDataStateMachine: TemplateFullAccountConfigDataStateMachine,
  private val electrumServerDataStateMachine: ElectrumServerDataStateMachine,
  private val firmwareDataStateMachine: FirmwareDataStateMachine,
  private val fiatCurrencyRepository: FiatCurrencyRepository,
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

    val templateFullAccountConfigData = templateFullAccountConfigDataStateMachine.model(Unit)

    val appData: AppData =
      when (templateFullAccountConfigData) {
        LoadingTemplateFullAccountConfigData -> LoadingAppData
        is LoadedTemplateFullAccountConfigData -> {
          SyncServerBasedRepositoriesEffect(
            f8eEnvironment = templateFullAccountConfigData.config.f8eEnvironment
          )
          val blockingEffectsState = listOf(initializeFeatureFlagsEffectState)
          val allBlockingEffectsAreComplete = blockingEffectsState.all { it == COMPLETE }

          val electrumServerData =
            electrumServerDataStateMachine.model(
              ElectrumServerDataProps(
                f8eEnvironment = templateFullAccountConfigData.config.f8eEnvironment,
                network = templateFullAccountConfigData.config.bitcoinNetworkType
              )
            )

          val firmwareData =
            firmwareDataStateMachine.model(
              props =
                FirmwareDataProps(
                  isHardwareFake = templateFullAccountConfigData.config.isHardwareFake
                )
            )

          when (allBlockingEffectsAreComplete) {
            true -> {
              // Wait until the local feature flags are initialized and an appInstallation has
              // been created before fetching remote feature flags.
              InitializeRemoteFeatureFlagsEffect()

              AppLoadedData(
                templateFullAccountConfigData,
                electrumServerData,
                firmwareData
              )
            }

            false -> LoadingAppData
          }
        }
      }

    return appData
  }

  @Composable
  private fun SyncServerBasedRepositoriesEffect(f8eEnvironment: F8eEnvironment) {
    LaunchedEffect("sync-server-based-repository", f8eEnvironment) {
      launch {
        // TODO(W-6665): migrate to scoped worker
        fiatCurrencyRepository.updateFromServer(f8eEnvironment)
      }
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
  private fun InitializeRemoteFeatureFlagsEffect() {
    LaunchedEffect("initialize-remote-feature-flags") {
      featureFlagSyncer.initializeSyncLoop(scope = this)
      featureFlagSyncer.sync()
    }
  }

  @Composable
  private fun AppLoadedData(
    templateFullAccountConfigData: LoadedTemplateFullAccountConfigData,
    electrumServerData: ElectrumServerData,
    firmwareData: FirmwareData,
  ): AppData {
    val accountData =
      accountDataStateMachine.model(
        props = AccountDataProps(templateFullAccountConfigData)
      )
    return AppData.AppLoadedData(
      accountData = accountData,
      electrumServerData = electrumServerData,
      firmwareData = firmwareData
    )
  }
}
