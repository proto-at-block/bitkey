package build.wallet.statemachine.dev

import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListGroupModel

/**
 * Returns a section for the debug menu based on the current account:
 * - For active accounts, returns info for the current account config
 * - For pre-active accounts, returns info and update capability for the current account config
 */
interface AccountConfigUiStateMachine : StateMachine<AccountConfigProps, ListGroupModel?>

data class AccountConfigProps(
  val onBitcoinWalletClick: () -> Unit,
)
