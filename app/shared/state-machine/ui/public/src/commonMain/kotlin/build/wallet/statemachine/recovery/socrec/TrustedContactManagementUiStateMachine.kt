package build.wallet.statemachine.recovery.socrec

import build.wallet.bitkey.account.FullAccount
import build.wallet.f8e.socrec.SocRecRelationships
import build.wallet.recovery.socrec.SocRecFullAccountActions
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for managing Trusted Contacts (syncing/viewing, adding, removing).
 */
interface TrustedContactManagementUiStateMachine : StateMachine<TrustedContactManagementProps, ScreenModel>

data class TrustedContactManagementProps(
  val account: FullAccount,
  val socRecRelationships: SocRecRelationships,
  val socRecActions: SocRecFullAccountActions,
  val onExit: () -> Unit,
  val inviteCode: String? = null,
)
