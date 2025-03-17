package build.wallet.statemachine.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.RecoveryEntity
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.core.form.FormBodyModel

/**
 * State machine for removing an inheritance relationship
 */
interface RemovingRelationshipUiStateMachine : StateMachine<RemovingRelationshipUiProps, ScreenModel>

data class RemovingRelationshipUiProps(
  val account: FullAccount,
  val body: FormBodyModel,
  val recoveryEntity: RecoveryEntity,
  val onSuccess: () -> Unit,
  val onExit: () -> Unit,
)
