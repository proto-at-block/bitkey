package build.wallet.statemachine.account

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData

/**
 * A state machine when there is no existing active account and no existing
 * account in the process of being onboarded.
 *
 * Allows the customer to choose a method of account access – either by creating
 * a new account or recovering an existing account.
 */
interface ChooseAccountAccessUiStateMachine : StateMachine<ChooseAccountAccessUiProps, ScreenModel>

data class ChooseAccountAccessUiProps(
  val chooseAccountAccessData: NoActiveAccountData.GettingStartedData,
)
