package build.wallet.statemachine.data.account.create

import build.wallet.bitkey.keybox.Keybox
import build.wallet.onboarding.CreateFullAccountContext
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.account.CreateFullAccountData

/**
 * Data state machine for managing creation AND activation of a Full Account
 */
interface CreateFullAccountDataStateMachine :
  StateMachine<CreateFullAccountDataProps, CreateFullAccountData>

/**
 * @property onboardingKeybox Persisted onboarding keybox, if any.
 * @property context The context of the account creation, either a brand new account or one being
 * upgraded.
 * @property rollback Rolls the data state back to [ChoosingCreateOrRecoverState].
 */
data class CreateFullAccountDataProps(
  val onboardingKeybox: Keybox?,
  val context: CreateFullAccountContext,
  val rollback: () -> Unit,
)
