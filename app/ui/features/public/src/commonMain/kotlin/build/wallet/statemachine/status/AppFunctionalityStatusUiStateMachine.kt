package build.wallet.statemachine.status

import build.wallet.availability.AppFunctionalityStatus
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for the screen showing the status of the app functionality when the app
 * is in a state of limited functionality (AppFunctionalityStatus.LimitedFunctionality).
 */
interface AppFunctionalityStatusUiStateMachine : StateMachine<AppFunctionalityStatusUiProps, ScreenModel>

data class AppFunctionalityStatusUiProps(
  val onClose: () -> Unit,
  val status: AppFunctionalityStatus.LimitedFunctionality,
)
