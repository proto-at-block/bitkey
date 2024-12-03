package build.wallet.statemachine.moneyhome.card.pendingclaim

import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.moneyhome.card.CardModel

/**
 * A state machine for the pending claim card UI on money home
 * If there is a non-dismissed pending claim card, this state machine will display it
 * If there is a locked claim, this state machine will display a card for it
 */
interface PendingClaimCardUiStateMachine :
  StateMachine<PendingClaimCardUiProps, List<CardModel>>

/**
 * @param onClick The action to take when the action button is clicked.
 * This is either dismisses the card if it is pending, or navigates to the claim details if it is locked.
 */
data class PendingClaimCardUiProps(
  val onClick: (() -> Unit)?,
)
