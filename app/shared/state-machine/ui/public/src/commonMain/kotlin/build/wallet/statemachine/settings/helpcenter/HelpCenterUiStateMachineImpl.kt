package build.wallet.statemachine.settings.helpcenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiState.ViewingContactUsUiState
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiState.ViewingFaqUiState
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiState.ViewingHelpCenterUiState

class HelpCenterUiStateMachineImpl(
  private val inAppBrowserNavigator: InAppBrowserNavigator,
) : HelpCenterUiStateMachine {
  @Composable
  override fun model(props: HelpCenterUiProps): ScreenModel {
    var uiState: HelpCenterUiState by remember {
      mutableStateOf(ViewingHelpCenterUiState)
    }

    return when (uiState) {
      is ViewingHelpCenterUiState ->
        ScreenModel(
          body =
            HelpCenterScreenModel(
              onFaqClick = {
                uiState = ViewingFaqUiState
              },
              onContactUsClick = {
                uiState = ViewingContactUsUiState
              },
              onBack = props.onBack
            )
        )

      is ViewingContactUsUiState ->
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = "https://support.bitkey.build/hc/requests/new",
              onClose = {
                uiState = ViewingHelpCenterUiState
              }
            )
          }
        ).asModalScreen()

      is ViewingFaqUiState ->
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = "https://support.bitkey.build/hc",
              onClose = {
                uiState = ViewingHelpCenterUiState
              }
            )
          }
        ).asModalScreen()
    }
  }
}

private sealed interface HelpCenterUiState {
  /**
   * Viewing the Main Help Center screen
   */
  data object ViewingHelpCenterUiState : HelpCenterUiState

  /**
   * Viewing the Contact us screen
   */
  data object ViewingContactUsUiState : HelpCenterUiState

  /**
   * Viewing the Faq screen
   */
  data object ViewingFaqUiState : HelpCenterUiState
}
