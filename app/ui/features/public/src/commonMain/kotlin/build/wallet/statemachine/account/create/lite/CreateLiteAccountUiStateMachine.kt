package build.wallet.statemachine.account.create.lite

import build.wallet.bitkey.account.Account
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * A state machine for creating and onboarding a "lite" (i.e. Recovery Contact only) customer account.
 *
 * Note: this is for creating the account in it's entirety. If you want to just enroll as
 * a Recovery Contact and already have an account, you should use
 * [TrustedContactEnrollmentUiStateMachine].
 */
interface CreateLiteAccountUiStateMachine : StateMachine<CreateLiteAccountUiProps, ScreenModel>

data class CreateLiteAccountUiProps(
  val onBack: () -> Unit,
  val showBeTrustedContactIntroduction: Boolean,
  val inviteCode: String?,
  val onAccountCreated: (Account) -> Unit,
)
