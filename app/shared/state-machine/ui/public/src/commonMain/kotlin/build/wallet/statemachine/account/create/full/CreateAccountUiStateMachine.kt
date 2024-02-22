package build.wallet.statemachine.account.create.full

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.account.CreateFullAccountData

/**
 * A state machine for creating a brand new keybox.
 */
interface CreateAccountUiStateMachine : StateMachine<CreateAccountUiProps, ScreenModel>

/**
 * [createFullAccountData] - data reflecting the state of the account creation
 */
data class CreateAccountUiProps(
  val createFullAccountData: CreateFullAccountData,
  val isHardwareFake: Boolean,
)
