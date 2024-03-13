package build.wallet.statemachine.settings.helpcenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiState.ViewingFaqUiState

class HelpCenterUiStateMachineImpl(
  private val inAppBrowserNavigator: InAppBrowserNavigator,
) : HelpCenterUiStateMachine {
  @Composable
  override fun model(props: HelpCenterUiProps): ScreenModel {
    var uiState: HelpCenterUiState by remember {
      mutableStateOf(ViewingFaqUiState)
    }

    return when (uiState) {
      is ViewingFaqUiState ->
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = "https://support.bitkey.build/hc",
              onClose = props.onBack
            )
          }
        ).asModalScreen()
    }
  }
}

private sealed interface HelpCenterUiState {
  /**
   * Viewing the Faq screen
   */
  data object ViewingFaqUiState : HelpCenterUiState
}
