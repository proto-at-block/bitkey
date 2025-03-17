package build.wallet.statemachine.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.core.form.FormBodyModel

/**
 * State machine for canceling an inheritance claim
 */
interface CancelingClaimUiStateMachine : StateMachine<CancelingClaimUiProps, ScreenModel>

data class CancelingClaimUiProps(
  val account: FullAccount,
  val body: FormBodyModel,
  val relationshipId: RelationshipId,
  val onSuccess: () -> Unit,
  val onExit: () -> Unit,
)
