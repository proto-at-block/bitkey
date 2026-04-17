package build.wallet.statemachine.core.input

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.AddTouchpointClientErrorCode
import build.wallet.email.Email
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for handling email input and validation
 */
interface EmailInputUiStateMachine : StateMachine<EmailInputUiProps, ScreenModel>

/**
 * Email input props
 *
 * @property onClose - invoked once the flow is closed
 * @property previousEmail - the email they may have already been entered in the flow, null when there is none
 * @property onEmailEntered - invoked once the email has been inputted and the user continues. Takes
 * a string as the email as the input
 */
data class EmailInputUiProps(
  val dataInputStyle: DataInputStyle,
  val onClose: (() -> Unit)? = null,
  /**
   * When true, shows a close (X) button instead of a back (<) button in the toolbar.
   * Use true when entering from Settings, false when entering from onboarding/recovery.
   */
  val isCloseButton: Boolean = false,
  val previousEmail: Email? = null,
  val subline: String? = null,
  val sublineModel: LabelModel? = null,
  val onEmailEntered: (
    email: Email,
    onError: (error: F8eError<AddTouchpointClientErrorCode>) -> Unit,
  ) -> Unit,
)
