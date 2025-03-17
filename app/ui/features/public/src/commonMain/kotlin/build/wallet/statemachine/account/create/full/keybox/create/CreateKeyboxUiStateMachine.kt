package build.wallet.statemachine.account.create.full.keybox.create

import build.wallet.bitkey.account.FullAccount
import build.wallet.onboarding.CreateFullAccountContext
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * A state machine for creating a brand new keybox.
 */
interface CreateKeyboxUiStateMachine : StateMachine<CreateKeyboxUiProps, ScreenModel>

data class CreateKeyboxUiProps(
  val context: CreateFullAccountContext,
  val onExit: () -> Unit,
  val onAccountCreated: (FullAccount) -> Unit,
)
