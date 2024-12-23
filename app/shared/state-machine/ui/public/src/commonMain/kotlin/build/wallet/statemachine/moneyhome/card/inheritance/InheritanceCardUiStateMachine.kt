package build.wallet.statemachine.moneyhome.card.inheritance

import build.wallet.bitkey.inheritance.InheritanceClaim
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.moneyhome.card.CardModel

/**
 * A state machine for the presenting cards for various states of the inheritance flow on money home
 */
interface InheritanceCardUiStateMachine :
  StateMachine<InheritanceCardUiProps, List<CardModel>>

/**
 * @param onClick The action to take when the action button is clicked.
 * This is either dismisses the card if it is pending, or navigates to the claim details if it is locked.
 */
data class InheritanceCardUiProps(
  val onClick: ((InheritanceClaim) -> Unit)?,
)
