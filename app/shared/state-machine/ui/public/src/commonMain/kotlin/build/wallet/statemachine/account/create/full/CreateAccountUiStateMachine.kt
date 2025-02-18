package build.wallet.statemachine.account.create.full

import build.wallet.bitkey.account.FullAccount
import build.wallet.onboarding.CreateFullAccountContext
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * A state machine for creating a brand new keybox.
 */
interface CreateAccountUiStateMachine : StateMachine<CreateAccountUiProps, ScreenModel>

/**
 * Props for the [CreateAccountUiStateMachine].
 *
 * @property context The context in which the account is being created.
 * @property fullAccount The full account that is being created. This is only present if the account
 * was previously created but not activated by completing onboarding
 * @property rollback Callback to rollback the account creation process.
 * @property onOnboardingComplete Callback to call when onboarding is complete.
 */
data class CreateAccountUiProps(
  val context: CreateFullAccountContext,
  val fullAccount: FullAccount? = null,
  val rollback: () -> Unit,
  val onOnboardingComplete: (FullAccount) -> Unit,
)
