package build.wallet.statemachine.cloud

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.RECTIFYING_CLOUD_ERROR
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.cloud.RectifiableErrorHandlingUiState.AttemptingRectificationState
import build.wallet.statemachine.cloud.RectifiableErrorHandlingUiState.ShowingExplanationState
import build.wallet.statemachine.cloud.RectifiableErrorMessages.Companion.RectifiableErrorAccessMessages
import build.wallet.statemachine.cloud.RectifiableErrorMessages.Companion.RectifiableErrorCreateFullMessages
import build.wallet.statemachine.cloud.RectifiableErrorMessages.Companion.RectifiableErrorCreateLiteMessages
import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.recovery.RecoverySegment

@BitkeyInject(ActivityScope::class)
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
          onBack = {
            props.onFailure(null)
          },
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
              onClick = {
                props.onFailure(null)
              }
            ),
          eventTrackerScreenId = props.screenId,
          errorData = props.errorData ?: ErrorData(
            segment = props.messages.defaultErrorDataSegment,
            actionDescription = "Handling rectifiable cloud backup error",
            cause = props.rectifiableError
          )
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

private val RectifiableErrorMessages.defaultErrorDataSegment: AppSegment
  get() =
    when (this) {
      RectifiableErrorAccessMessages -> RecoverySegment.CloudBackup.FullAccount.Restoration
      RectifiableErrorCreateFullMessages -> RecoverySegment.CloudBackup.FullAccount.Upload
      RectifiableErrorCreateLiteMessages -> RecoverySegment.CloudBackup.LiteAccount.Upload
      else -> RecoverySegment.CloudBackup.FullAccount.Upload
    }
