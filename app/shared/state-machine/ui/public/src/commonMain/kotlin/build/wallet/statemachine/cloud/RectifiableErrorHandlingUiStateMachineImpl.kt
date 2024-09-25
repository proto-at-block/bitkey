package build.wallet.statemachine.cloud

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.RECTIFYING_CLOUD_ERROR
import build.wallet.statemachine.cloud.RectifiableErrorHandlingUiState.AttemptingRectificationState
import build.wallet.statemachine.cloud.RectifiableErrorHandlingUiState.ShowingExplanationState
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModelWithOptionalErrorData
import build.wallet.statemachine.core.LabelModel.StringModel
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
        ErrorFormBodyModelWithOptionalErrorData(
          onBack = {
            props.onFailure(null)
          },
          title = props.messages.title,
          subline = StringModel(props.messages.subline),
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
              onClick = {
                props.onFailure(null)
              }
            ),
          eventTrackerScreenId = props.screenId,
          errorData = props.errorData
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
