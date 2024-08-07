package build.wallet.statemachine.data.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import build.wallet.f8e.F8eEnvironment
import build.wallet.feature.FeatureFlagService
import build.wallet.money.currency.FiatCurrencyRepository
import build.wallet.statemachine.data.app.AppData.LoadingAppData
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
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
class AppDataStateMachineImpl(
  private val featureFlagService: FeatureFlagService,
  private val accountDataStateMachine: AccountDataStateMachine,
  private val templateFullAccountConfigDataStateMachine: TemplateFullAccountConfigDataStateMachine,
  private val electrumServerDataStateMachine: ElectrumServerDataStateMachine,
  private val firmwareDataStateMachine: FirmwareDataStateMachine,
  private val fiatCurrencyRepository: FiatCurrencyRepository,
) : AppDataStateMachine {
  @Composable
  override fun model(props: Unit): AppData {
    val templateFullAccountConfigData = templateFullAccountConfigDataStateMachine.model(Unit)
    val featureFlagsInitialized by featureFlagService.featureFlagsInitialized.collectAsState()

    val appData: AppData =
      when (templateFullAccountConfigData) {
        LoadingTemplateFullAccountConfigData -> LoadingAppData
        is LoadedTemplateFullAccountConfigData -> {
          SyncServerBasedRepositoriesEffect(
            f8eEnvironment = templateFullAccountConfigData.config.f8eEnvironment
          )
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

          if (featureFlagsInitialized) {
            AppLoadedData(
              templateFullAccountConfigData = templateFullAccountConfigData,
              electrumServerData = electrumServerData,
              firmwareData = firmwareData
            )
          } else {
            LoadingAppData
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
