package build.wallet.statemachine.dev

import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListGroupModel

/**
 * State machine for picking f8e environment to use for onboarding a new account.
 */
interface F8eEnvironmentPickerUiStateMachine :
  StateMachine<F8eEnvironmentPickerUiProps, ListGroupModel?>

data class F8eEnvironmentPickerUiProps(
  val openCustomUrlInput: (customUrl: String) -> Unit,
)
