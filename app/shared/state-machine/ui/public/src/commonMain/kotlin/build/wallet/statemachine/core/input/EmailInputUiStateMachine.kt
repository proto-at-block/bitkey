package build.wallet.statemachine.core.input

import build.wallet.email.Email
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.AddTouchpointClientErrorCode
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
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
 * @property skipBottomSheetProvider - bottom sheet to show when skip toolbar button is clicked.
 * Null if no Skip button should be shown
 */
data class EmailInputUiProps(
  val dataInputStyle: DataInputStyle,
  val onClose: () -> Unit,
  val previousEmail: Email? = null,
  val subline: String? = null,
  val onEmailEntered: (
    email: Email,
    onError: (error: F8eError<AddTouchpointClientErrorCode>) -> Unit,
  ) -> Unit,
  val skipBottomSheetProvider: ((onBack: () -> Unit) -> SheetModel)?,
)
