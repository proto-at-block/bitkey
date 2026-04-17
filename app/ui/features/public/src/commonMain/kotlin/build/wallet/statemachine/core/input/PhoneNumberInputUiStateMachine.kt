package build.wallet.statemachine.core.input

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.AddTouchpointClientErrorCode
import build.wallet.phonenumber.PhoneNumber
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for entering a phone number.
 */
interface PhoneNumberInputUiStateMachine : StateMachine<PhoneNumberInputUiProps, ScreenModel>

/**
 * @property onClose - handler for close navigation.
 * @property onSubmitPhoneNumber - handler for when a valid phone number is entered and
 * continue is pressed
 * @property secondaryButtonOnClick if [secondaryButtonText] is not null and this property is
 * [null] then secondary click is considered as `onBack`
 * @property onSkipSecondaryButton - handler for skip secondary button shown above the primary
 * Continue button. Null if no skip secondary button should be shown.
 */
data class PhoneNumberInputUiProps(
  val dataInputStyle: DataInputStyle,
  val primaryButtonText: String,
  val prefillValue: PhoneNumber?,
  val subline: String?,
  val sublineModel: LabelModel? = null,
  val primaryButtonOnClick: (() -> Unit)?,
  val secondaryButtonText: String?,
  val secondaryButtonOnClick: (() -> Unit)?,
  val onClose: (() -> Unit)? = null,
  /**
   * When true, shows a close (X) button instead of a back (<) button in the toolbar.
   * Use true when entering from Settings, false when entering from onboarding/recovery.
   */
  val isCloseButton: Boolean = false,
  val onSubmitPhoneNumber: (
    phoneNumber: PhoneNumber,
    onError: (error: F8eError<AddTouchpointClientErrorCode>) -> Unit,
  ) -> Unit,
  val onSkipSecondaryButton: (() -> Unit)? = null,
)
