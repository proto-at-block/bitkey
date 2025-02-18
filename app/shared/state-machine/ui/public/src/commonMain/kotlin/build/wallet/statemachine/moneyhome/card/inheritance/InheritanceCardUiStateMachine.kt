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
 * @param completeClaim if a beneficiary taps the button to complete the claim
 * @param denyClaim if a benefactor taps the button to deny the claim
 * @param moveFundsCallToAction if a benefactor taps the button on the danger CTA
 *
 */
data class InheritanceCardUiProps(
  val claimFilter: (InheritanceClaim) -> Boolean = { true },
  val isDismissible: Boolean = true,
  val includeDismissed: Boolean = false,
  val completeClaim: (InheritanceClaim) -> Unit,
  val denyClaim: (InheritanceClaim) -> Unit,
  val moveFundsCallToAction: () -> Unit,
)
