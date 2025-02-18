package bitkey.ui.screens.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.debug.DebugOptionsService
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.demo.DemoModeF8eClient
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.time.MinimumLoadingDuration
import build.wallet.time.withMinimumDelay
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.first

data class DemoCodeEntrySubmissionScreen(
  val demoModeCode: String,
) : Screen

@BitkeyInject(ActivityScope::class, boundTypes = [DemoCodeEntrySubmissionScreenPresenter::class])
class DemoCodeEntrySubmissionScreenPresenter(
  private val debugOptionsService: DebugOptionsService,
  private val minimumLoadingDuration: MinimumLoadingDuration,
  private val demoModeF8eClient: DemoModeF8eClient,
) : ScreenPresenter<DemoCodeEntrySubmissionScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: DemoCodeEntrySubmissionScreen,
  ): ScreenModel {
    LaunchedEffect("enable-demo-code") {
      withMinimumDelay(minimumLoadingDuration.value) {
        enableDemoMode(screen.demoModeCode)
      }.onSuccess {
        navigator.goTo(DemoModeEnabledScreen)
      }.onFailure {
        navigator.goTo(DemoModeCodeEntryScreen)
      }
    }

    return LoadingBodyModel(
      id = DemoCodeTrackerScreenId.DEMO_MODE_CODE_SUBMISSION,
      message = "Submitting demo mode code...",
      onBack = {
        navigator.goTo(DemoModeCodeEntryScreen)
      }
    ).asModalScreen()
  }

  // TODO: extract into service
  private suspend fun enableDemoMode(code: String): Result<Unit, Error> =
    coroutineBinding {
      val debugOptions = debugOptionsService.options().first()
      demoModeF8eClient.initiateDemoMode(debugOptions.f8eEnvironment, code).bind()
      debugOptionsService.setIsHardwareFake(value = true).bind()
      debugOptionsService.setIsTestAccount(value = true).bind()
    }
}
