package build.wallet.statemachine.recovery.socrec.view

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.Invitation
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State Machine for viewing the details of a single invitation.
 */
interface ViewingInvitationUiStateMachine : StateMachine<ViewingInvitationProps, ScreenModel>

data class ViewingInvitationProps(
  val hostScreen: ScreenModel,
  val fullAccount: FullAccount,
  val invitation: Invitation,
  val onExit: () -> Unit,
)
