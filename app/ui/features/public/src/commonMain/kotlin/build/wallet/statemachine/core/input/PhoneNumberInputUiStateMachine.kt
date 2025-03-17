package build.wallet.statemachine.core.input

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.AddTouchpointClientErrorCode
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
 * @property secondaryButtonOnClick if [secondaryButtonText] is not null and this property is
 * [null] then secondary click is considered as `onBack`
 */
data class PhoneNumberInputUiProps(
  val dataInputStyle: DataInputStyle,
  val primaryButtonText: String,
  val prefillValue: PhoneNumber?,
  val subline: String?,
  val primaryButtonOnClick: (() -> Unit)?,
  val secondaryButtonText: String?,
  val secondaryButtonOnClick: (() -> Unit)?,
  val onClose: () -> Unit,
  val onSubmitPhoneNumber: (
    phoneNumber: PhoneNumber,
    onError: (error: F8eError<AddTouchpointClientErrorCode>) -> Unit,
  ) -> Unit,
  val skipBottomSheetProvider: ((onBack: () -> Unit) -> SheetModel)?,
)
