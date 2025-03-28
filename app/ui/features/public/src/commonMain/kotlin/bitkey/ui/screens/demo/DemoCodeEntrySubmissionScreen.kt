package bitkey.ui.screens.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import bitkey.demo.DemoModeService
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.time.MinimumLoadingDuration
import build.wallet.time.withMinimumDelay
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

data class DemoCodeEntrySubmissionScreen(
  val demoModeCode: String,
) : Screen

@BitkeyInject(ActivityScope::class)
class DemoCodeEntrySubmissionScreenPresenter(
  private val minimumLoadingDuration: MinimumLoadingDuration,
  private val demoModeService: DemoModeService,
) : ScreenPresenter<DemoCodeEntrySubmissionScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: DemoCodeEntrySubmissionScreen,
  ): ScreenModel {
    LaunchedEffect("enable-demo-code") {
      withMinimumDelay(minimumLoadingDuration.value) {
        demoModeService.enable(screen.demoModeCode)
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
}
