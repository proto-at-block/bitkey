package build.wallet.statemachine.education

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for showing Education content screens
 */
interface EducationUiStateMachine : StateMachine<EducationUiProps, ScreenModel>

/**
 * Initializes the content to be displayed in the Education screen
 *
 * @property items - The list of [EducationItem]s to display in succession on the screen
 * @property onExit - The callback to invoke when the user dismisses the Education screen
 * @property onContinue - The callback to invoke when the user has completed the Education screen
 */
data class EducationUiProps(
  val items: List<EducationItem>,
  val onExit: () -> Unit,
  val onContinue: () -> Unit,
)
