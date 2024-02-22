package build.wallet.statemachine.core.input

import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.AddTouchpointClientErrorCode
import build.wallet.phonenumber.PhoneNumber
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for entering a phone number.
 */
interface PhoneNumberInputUiStateMachine : StateMachine<PhoneNumberInputUiProps, ScreenModel>

/**
 * @property onClose - handler for close navigation.
 * @property onSubmitPhoneNumber - handler for when a valid phone number is entered and
 * continue is pressed
 * @property onSkip - handler for skip after receiving [InvalidCountryCodeError]. Null if no
 * option to Skip should be shown (i.e. when entering this flow from Settings, not Onboarding).
 * @property skipBottomSheetProvider - bottom sheet to show when skip toolbar button is clicked.
 * Null if no Skip button should be shown
 */
data class PhoneNumberInputUiProps(
  val dataInputStyle: DataInputStyle,
  val prefillValue: PhoneNumber?,
  val onClose: () -> Unit,
  val onSubmitPhoneNumber: (
    phoneNumber: PhoneNumber,
    onError: (error: F8eError<AddTouchpointClientErrorCode>) -> Unit,
  ) -> Unit,
  val onSkip: (() -> Unit)?,
  val skipBottomSheetProvider: ((onBack: () -> Unit) -> SheetModel)?,
)
