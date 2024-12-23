package build.wallet.statemachine.inheritance.claims.complete

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * Prompts the user to confirm and complete their inheritnace transaction.
 */
interface CompleteInheritanceClaimUiStateMachine : StateMachine<CompleteInheritanceClaimUiStateMachineProps, ScreenModel>

data class CompleteInheritanceClaimUiStateMachineProps(
  /**
   * The ID of the relationship to complete the inheritance claim for.
   */
  val relationshipId: RelationshipId,
  val account: FullAccount,
  val onExit: () -> Unit,
)
