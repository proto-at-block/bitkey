package build.wallet.statemachine.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.RECTIFYING_CLOUD_ERROR
import build.wallet.statemachine.cloud.RectifiableErrorHandlingUiState.AttemptingRectificationState
import build.wallet.statemachine.cloud.RectifiableErrorHandlingUiState.ShowingExplanationState
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel

class RectifiableErrorHandlingUiStateMachineImpl(
  private val cloudBackupRectificationNavigator: CloudBackupRectificationNavigator,
) : RectifiableErrorHandlingUiStateMachine {
  @Composable
  override fun model(props: RectifiableErrorHandlingProps): ScreenModel {
    var state: RectifiableErrorHandlingUiState by remember {
      mutableStateOf(ShowingExplanationState)
    }
    return when (state) {
      ShowingExplanationState ->
        ErrorFormBodyModel(
          onBack = props.onFailure,
          title = props.messages.title,
          subline = props.messages.subline,
          primaryButton =
            ButtonDataModel(
              text = "Try again",
              onClick = {
                state = AttemptingRectificationState
              }
            ),
          secondaryButton =
            ButtonDataModel(
              text = "Cancel",
              onClick = props.onFailure
            ),
          eventTrackerScreenId = props.screenId
        )
      AttemptingRectificationState -> {
        cloudBackupRectificationNavigator.navigate(
          data = props.rectifiableError.data,
          onReturn = props.onReturn
        )
        LoadingBodyModel(
          onBack = {
            state = ShowingExplanationState
          },
          id = RECTIFYING_CLOUD_ERROR
        )
      }
    }.asScreen(props.presentationStyle)
  }
}

private sealed interface RectifiableErrorHandlingUiState {
  data object ShowingExplanationState : RectifiableErrorHandlingUiState

  data object AttemptingRectificationState : RectifiableErrorHandlingUiState
}
