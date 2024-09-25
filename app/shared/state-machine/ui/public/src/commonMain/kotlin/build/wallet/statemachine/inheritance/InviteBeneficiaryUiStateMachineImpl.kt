package build.wallet.statemachine.inheritance

import androidx.compose.runtime.Composable
import build.wallet.inheritance.InheritanceService
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiProps
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiStateMachine
import com.github.michaelbull.result.coroutines.coroutineBinding

class InviteBeneficiaryUiStateMachineImpl(
  private val addingTrustedContactUiStateMachine: AddingTrustedContactUiStateMachine,
  private val inheritanceService: InheritanceService,
) : InviteBeneficiaryUiStateMachine {
  @Composable
  override fun model(props: InviteBeneficiaryUiProps): ScreenModel {
    return addingTrustedContactUiStateMachine.model(
      props = AddingTrustedContactUiProps(
        account = props.account,
        onAddTc = { trustedContactAlias, hardwareProofOfPossession ->
          coroutineBinding {
            inheritanceService
              .createInheritanceInvitation(
                trustedContactAlias = trustedContactAlias,
                hardwareProofOfPossession = hardwareProofOfPossession
              )
              .bind()
          }
        },
        onInvitationShared = props.onExit,
        onExit = props.onExit
      )
    )
  }
}
