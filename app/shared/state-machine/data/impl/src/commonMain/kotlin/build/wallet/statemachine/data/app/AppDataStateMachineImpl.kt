@file:OptIn(ExperimentalObjCRefinement::class)

package build.wallet.statemachine.data.app

import androidx.compose.runtime.*
import build.wallet.debug.DebugOptionsService
import build.wallet.feature.FeatureFlagService
import build.wallet.money.currency.FiatCurrencyRepository
import build.wallet.statemachine.data.app.AppData.LoadingAppData
import build.wallet.statemachine.data.keybox.AccountDataStateMachine
import kotlinx.coroutines.launch
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
class AppDataStateMachineImpl(
  private val featureFlagService: FeatureFlagService,
  private val accountDataStateMachine: AccountDataStateMachine,
  private val fiatCurrencyRepository: FiatCurrencyRepository,
  private val debugOptionsService: DebugOptionsService,
) : AppDataStateMachine {
  @Composable
  override fun model(props: Unit): AppData {
    val featureFlagsInitialized by featureFlagService.featureFlagsInitialized.collectAsState()

    SyncServerBasedRepositoriesEffect()

    return if (featureFlagsInitialized) {
      val accountData = accountDataStateMachine.model(Unit)
      return AppData.AppLoadedData(
        accountData = accountData
      )
    } else {
      LoadingAppData
    }
  }

  @Composable
  private fun SyncServerBasedRepositoriesEffect() {
    val debugOptions = remember { debugOptionsService.options() }
      .collectAsState(initial = null)
      .value ?: return

    LaunchedEffect("sync-server-based-repository", debugOptions.f8eEnvironment) {
      launch {
        // TODO(W-6665): migrate to scoped worker
        fiatCurrencyRepository.updateFromServer(debugOptions.f8eEnvironment)
      }
    }
  }
}
