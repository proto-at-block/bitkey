package build.wallet.statemachine.core.input

import androidx.compose.runtime.*
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.AddTouchpointClientErrorCode
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.email.Email
import build.wallet.email.EmailValidator
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.input.DataInputStyle.Edit
import build.wallet.statemachine.core.input.DataInputStyle.Enter
import build.wallet.statemachine.core.input.EmailInputUiStateMachineImpl.State.BottomSheetState.Hidden
import build.wallet.statemachine.core.input.EmailInputUiStateMachineImpl.State.BottomSheetState.ShowingErrorSheet
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer

@BitkeyInject(ActivityScope::class)
class EmailInputUiStateMachineImpl(
  private val emailValidator: EmailValidator,
) : EmailInputUiStateMachine {
  @Composable
  override fun model(props: EmailInputUiProps): ScreenModel {
    var state by remember(props.previousEmail) {
      val email = props.previousEmail ?: Email(value = "")
      val isValidEmail = emailValidator.validateEmail(email)
      mutableStateOf(State(email = email, isValid = isValidEmail))
    }

    return EmailInputScreenModel(
      title = when (props.dataInputStyle) {
        Enter -> "Enter your email address"
        Edit -> "Edit your email address"
      },
      subline = props.subline,
      sublineModel = props.sublineModel,
      value = state.email.value,
      primaryButton = ButtonModel(
        text = "Continue",
        isEnabled = state.isValid,
        isLoading = state.isLoading,
        size = Footer,
        onClick = StandardClick {
          if (state.isValid) {
            state = state.copy(isLoading = true)
          }
          val trimmedEmail = Email(value = state.email.value.trim())
          props.onEmailEntered(trimmedEmail) { error ->
            state = state.copy(
              isLoading = false,
              bottomSheetState = ShowingErrorSheet(error)
            )
          }
        }
      ),
      onValueChange = { updatedEmailText: String ->
        val updatedEmail = Email(value = updatedEmailText)
        state = State(
          email = updatedEmail,
          isValid = emailValidator.validateEmail(updatedEmail)
        )
      },
      onClose = props.onClose?.let { { it() } },
      isCloseButton = props.isCloseButton
    ).asModalScreen(
      bottomSheetModel = when (val bottomSheetState = state.bottomSheetState) {
        is Hidden -> null
        is ShowingErrorSheet ->
          when (bottomSheetState.error) {
            is F8eError.SpecificClientError ->
              when (bottomSheetState.error.errorCode) {
                AddTouchpointClientErrorCode.TOUCHPOINT_ALREADY_ACTIVE ->
                  EmailTouchpointAlreadyActiveErrorSheetModel(
                    onBack = { state = state.copy(bottomSheetState = Hidden) }
                  )
                AddTouchpointClientErrorCode.UNSUPPORTED_COUNTRY_CODE ->
                  error("Unexpected error code for email touchpoint")
                AddTouchpointClientErrorCode.INVALID_EMAIL_ADDRESS ->
                  EmailTouchpointInvalidErrorSheetModel(
                    onBack = { state = state.copy(bottomSheetState = Hidden) }
                  )
              }
            else -> EmailInputErrorSheetModel(
              isConnectivityError = bottomSheetState.error is F8eError.ConnectivityError,
              onBack = { state = state.copy(bottomSheetState = Hidden) }
            )
          }
      }
    )
  }

  private data class State(
    val email: Email,
    val bottomSheetState: BottomSheetState = Hidden,
    val isLoading: Boolean = false,
    val isValid: Boolean = false,
  ) {
    sealed interface BottomSheetState {
      data object Hidden : BottomSheetState

      data class ShowingErrorSheet(
        val error: F8eError<AddTouchpointClientErrorCode>,
      ) : BottomSheetState
    }
  }
}
