package build.wallet.statemachine.settings.full.feedback.old

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.settings.full.feedback.old.OldFeedbackUiState.ViewingContactUsUiState
import build.wallet.statemachine.settings.full.feedback.old.OldFeedbackUiState.ViewingFeedbackUiState

class OldFeedbackUiStateMachineImpl(
  private val inAppBrowserNavigator: InAppBrowserNavigator,
) : OldFeedbackUiStateMachine {
  @Composable
  override fun model(props: OldFeedbackUiProps): ScreenModel {
    var uiState: OldFeedbackUiState by remember {
      mutableStateOf(ViewingFeedbackUiState)
    }

    return when (uiState) {
      ViewingContactUsUiState ->
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = "https://support.bitkey.build/hc/requests/new",
              onClose = {
                uiState = ViewingFeedbackUiState
              }
            )
          }
        ).asModalScreen()
      ViewingFeedbackUiState ->
        ScreenModel(
          body =
            OldFeedbackScreenModel(
              onContactUsClick = {
                uiState = ViewingContactUsUiState
              },
              onBack = props.onBack
            )
        )
    }
  }
}

private sealed interface OldFeedbackUiState {
  /**
   * Viewing the main Feedback link screen
   */
  data object ViewingFeedbackUiState : OldFeedbackUiState

  /**
   * Viewing the Contact Us browser form screen
   */
  data object ViewingContactUsUiState : OldFeedbackUiState
}
