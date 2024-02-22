package build.wallet.statemachine.core.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.AddTouchpointClientErrorCode
import build.wallet.phonenumber.PhoneNumber
import build.wallet.phonenumber.PhoneNumberFormatter
import build.wallet.phonenumber.PhoneNumberValidator
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.input.DataInputStyle.Edit
import build.wallet.statemachine.core.input.DataInputStyle.Enter
import build.wallet.statemachine.core.input.PhoneNumberInputUiState.BottomSheetState.Hidden
import build.wallet.statemachine.core.input.PhoneNumberInputUiState.BottomSheetState.ShowingErrorSheet
import build.wallet.statemachine.core.input.PhoneNumberInputUiState.BottomSheetState.ShowingSkipSheet
import build.wallet.statemachine.core.input.PhoneNumberInputUiState.EntryState.Complete
import build.wallet.statemachine.core.input.PhoneNumberInputUiState.EntryState.Incomplete
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer

class PhoneNumberInputUiStateMachineImpl(
  private val phoneNumberFormatter: PhoneNumberFormatter,
  private val phoneNumberValidator: PhoneNumberValidator,
) : PhoneNumberInputUiStateMachine {
  @Composable
  override fun model(props: PhoneNumberInputUiProps): ScreenModel {
    var state by remember {
      val dialingCode = phoneNumberValidator.dialingCodeForCurrentRegion()
      val exampleNumber = phoneNumberValidator.exampleFormattedNumberForCurrentRegion()
      val validatedPhoneNumber =
        phoneNumberValidator.validatePhoneNumber(
          number = props.prefillValue?.formattedE164Value ?: ""
        )
      val entryState =
        when (validatedPhoneNumber) {
          // Start out with the guessed country dialing code filled in when not given a prefill.
          null -> Incomplete(formattedValue = "+$dialingCode ")
          else -> Complete(validatedPhoneNumber)
        }
      mutableStateOf(
        PhoneNumberInputUiState(
          placeholderText = exampleNumber ?: "Phone number",
          entryState = entryState,
          entrySelection = entryState.formattedValue.length..entryState.formattedValue.length
        )
      )
    }

    return PhoneNumberInputScreenModel(
      title =
        when (props.dataInputStyle) {
          Enter -> "Enter your phone number"
          Edit -> "Edit your phone number"
        },
      textFieldValue = state.entryState.formattedValue,
      textFieldSelection = state.entrySelection,
      textFieldPlaceholder = state.placeholderText,
      onTextFieldValueChange = { formattedNumber: String, formattedNumberSelection: IntRange ->
        val validatedPhoneNumber = phoneNumberValidator.validatePhoneNumber(formattedNumber)
        val newEntryState =
          when (validatedPhoneNumber) {
            null ->
              Incomplete(
                formattedValue = phoneNumberFormatter.formatPartialPhoneNumber(formattedNumber)
              )

            else -> Complete(validatedPhoneNumber)
          }

        // If the cursor was at the end, keep it at the end, otherwise keep it where it was
        // TODO (W-4305): Improve selection to always remain in the same numerical place,
        // ignoring formatting
        val newEntryStateSelection =
          when (formattedNumberSelection.first == formattedNumber.length) {
            true -> newEntryState.formattedValue.length..newEntryState.formattedValue.length
            false -> formattedNumberSelection
          }

        state =
          state.copy(
            entryState = newEntryState,
            entrySelection = newEntryStateSelection
          )
      },
      primaryButton =
        ButtonModel(
          text = "Continue",
          isEnabled =
            when (state.entryState) {
              is Complete -> true
              is Incomplete -> false
            },
          isLoading = state.buttonIsLoading,
          size = Footer,
          onClick =
            Click.standardClick {
              handleContinueClick(
                state = state,
                props = props,
                setState = {
                  state = it
                }
              )
            }
        ),
      onClose = { props.onClose() },
      onSkip =
        props.skipBottomSheetProvider?.let {
          {
            // Build the sheet with the onBack behavior
            val skipBottomSheet =
              it {
                state = state.copy(bottomSheetState = Hidden)
              }
            // Update the state to show the sheet
            state = state.copy(bottomSheetState = ShowingSkipSheet(skipBottomSheet))
          }
        },
      errorOverlayModel =
        when (val bottomSheetState = state.bottomSheetState) {
          is Hidden -> null
          is ShowingSkipSheet -> bottomSheetState.sheet
          is ShowingErrorSheet ->
            SheetModel(
              onClosed = { state = state.copy(bottomSheetState = Hidden) },
              body =
                when (bottomSheetState.error) {
                  is F8eError.SpecificClientError -> {
                    when (bottomSheetState.error.errorCode) {
                      AddTouchpointClientErrorCode.TOUCHPOINT_ALREADY_ACTIVE ->
                        PhoneNumberTouchpointAlreadyActiveErrorSheetModel(
                          onBack = { state = state.copy(bottomSheetState = Hidden) }
                        )

                      AddTouchpointClientErrorCode.UNSUPPORTED_COUNTRY_CODE ->
                        PhoneNumberUnsupportedCountryErrorSheetModel(
                          onBack = { state = state.copy(bottomSheetState = Hidden) },
                          onSkip = props.onSkip
                        )

                      AddTouchpointClientErrorCode.INVALID_EMAIL_ADDRESS ->
                        error("Unexpected error code for sms touchpoint")
                    }
                  }

                  else ->
                    PhoneNumberInputErrorSheetModel(
                      isConnectivityError = bottomSheetState.error is F8eError.ConnectivityError,
                      onBack = { state = state.copy(bottomSheetState = Hidden) }
                    )
                }
            )
        }
    )
  }
}

private data class PhoneNumberInputUiState(
  val placeholderText: String,
  val entryState: EntryState,
  val entrySelection: IntRange,
  val buttonIsLoading: Boolean = false,
  val bottomSheetState: BottomSheetState = Hidden,
) {
  sealed interface EntryState {
    val formattedValue: String

    /** The phone number entry is incomplete */
    data class Incomplete(
      override val formattedValue: String,
    ) : EntryState

    /** A valid phone number was entered, the entry is complete */
    data class Complete(
      val enteredPhoneNumber: PhoneNumber,
      override val formattedValue: String = enteredPhoneNumber.formattedDisplayValue,
    ) : EntryState
  }

  sealed interface BottomSheetState {
    data object Hidden : BottomSheetState

    data class ShowingSkipSheet(val sheet: SheetModel) : BottomSheetState

    data class ShowingErrorSheet(
      val error: F8eError<AddTouchpointClientErrorCode>,
    ) : BottomSheetState
  }
}

private fun handleContinueClick(
  state: PhoneNumberInputUiState,
  props: PhoneNumberInputUiProps,
  setState: (PhoneNumberInputUiState) -> Unit,
) {
  when (state.entryState) {
    is Complete -> {
      props.onSubmitPhoneNumber(state.entryState.enteredPhoneNumber) { error ->
        setState(
          state.copy(
            buttonIsLoading = false,
            bottomSheetState = ShowingErrorSheet(error)
          )
        )
      }
      setState(state.copy(buttonIsLoading = true))
    }
    is Incomplete -> Unit
  }
}
