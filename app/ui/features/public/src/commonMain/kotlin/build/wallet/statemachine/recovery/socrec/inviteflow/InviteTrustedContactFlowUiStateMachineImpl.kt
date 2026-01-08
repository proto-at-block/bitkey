package build.wallet.statemachine.recovery.socrec.inviteflow

import androidx.compose.runtime.Composable
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.relationships.RelationshipsService
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiProps
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiStateMachine

@BitkeyInject(ActivityScope::class)
class InviteTrustedContactFlowUiStateMachineImpl(
  private val addingTrustedContactUiStateMachine: AddingTrustedContactUiStateMachine,
  private val relationshipsService: RelationshipsService,
) : InviteTrustedContactFlowUiStateMachine {
  @Composable
  override fun model(props: InviteTrustedContactFlowUiProps): ScreenModel {
    return addingTrustedContactUiStateMachine.model(
      props =
        AddingTrustedContactUiProps(
          account = props.account,
          onAddTc = { trustedContactAlias, hardwareProofOfPossession ->
            relationshipsService
              .createInvitation(
                account = props.account,
                trustedContactAlias = trustedContactAlias,
                hardwareProofOfPossession = hardwareProofOfPossession,
                roles = setOf(TrustedContactRole.SocialRecoveryContact)
              )
          },
          onInvitationShared = props.onExit,
          onExit = props.onExit
        )
    )
  }
}
