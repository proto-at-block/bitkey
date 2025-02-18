package build.wallet.statemachine.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * This state machine manages the UI for denying a claim as the benefactor.
 */
interface DeclineInheritanceClaimUiStateMachine :
  StateMachine<DeclineInheritanceClaimUiProps, ScreenModel>

/**
 * @param fullAccount
 * @param claimId the claim being denied
 * @param onBack Callback to navigate back to the previous screen.
 */
data class DeclineInheritanceClaimUiProps(
  val fullAccount: FullAccount,
  val claimId: String,
  val onBack: () -> Unit,
  val onClaimDeclined: () -> Unit,
  val onBeneficiaryRemoved: () -> Unit,
)
