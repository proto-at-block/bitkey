package build.wallet.statemachine.inheritance.claims.start

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * Flow used by a beneficiary to start a claim for an inheritance.
 */
interface StartClaimUiStateMachine : StateMachine<StartClaimUiStateMachineProps, ScreenModel>

data class StartClaimUiStateMachineProps(
  /**
   * The account of the beneficiary starting the claim.
   */
  val account: FullAccount,
  /**
   * The associated inheritance relationship.
   */
  val relationshipId: RelationshipId,
  /**
   * Invoked to exit the flow successfully or to cancel.
   */
  val onExit: () -> Unit,
)
