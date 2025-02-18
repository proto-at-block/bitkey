package build.wallet.statemachine.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * This state machine manages any sheets that need to be presented due to an inheritance notification
 */
interface InheritanceClaimNotificationUiStateMachine :
  StateMachine<InheritanceClaimNotificationUiProps, ScreenModel>

/**
 * This is the action that can be taken based on an inheritance notification
 * CompleteClaim - Complete the inheritance claim
 * DenyClaim - Decline the inheritance claim
 * BenefactorInviteAccepted - The benefactor has accepted the invite, display the success screen
 */
enum class InheritanceNotificationAction {
  CompleteClaim,
  DenyClaim,
  BenefactorInviteAccepted,
}

/**
 *
 * @param fullAccount
 * @param action the action to take given the notification
 * @param claimId the claim associated with the notification
 * @param onBack Callback to navigate back to the previous screen.
 */
data class InheritanceClaimNotificationUiProps(
  val fullAccount: FullAccount,
  val action: InheritanceNotificationAction,
  val claimId: String?,
  val onBack: () -> Unit,
)
