package build.wallet.statemachine.trustedcontact

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * This state machine manages any sheets that need to be presented related to a recovery relationship
 */
interface RecoveryRelationshipNotificationUiStateMachine :
  StateMachine<RecoveryRelationshipNotificationUiProps, ScreenModel>

/**
 * This is the action that can be taken based on a recovery relationship notification
 * BenefactorInviteAccepted - The benefactor has accepted the invite, display the success screen
 * ProtectedCustomerInviteAccepted - The protected customer has accepted the invite, display the success screen
 */
enum class RecoveryRelationshipNotificationAction {
  BenefactorInviteAccepted,
  ProtectedCustomerInviteAccepted,
}

/**
 *
 * @param fullAccount
 * @param action the action to take given the notification
 * @param onBack Callback to navigate back to the previous screen.
 */
data class RecoveryRelationshipNotificationUiProps(
  val fullAccount: FullAccount,
  val action: RecoveryRelationshipNotificationAction,
  val recoveryRelationshipId: String,
  val onBack: () -> Unit,
)
