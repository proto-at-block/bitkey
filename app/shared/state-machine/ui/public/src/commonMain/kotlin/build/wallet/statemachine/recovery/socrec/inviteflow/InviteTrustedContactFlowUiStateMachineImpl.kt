package build.wallet.statemachine.recovery.socrec.inviteflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTaskDao
import build.wallet.recovery.socrec.SocRecRelationshipsRepository
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiProps
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiStateMachine
import com.github.michaelbull.result.coroutines.binding.binding

class InviteTrustedContactFlowUiStateMachineImpl(
  private val addingTrustedContactUiStateMachine: AddingTrustedContactUiStateMachine,
  private val gettingStartedTaskDao: GettingStartedTaskDao,
  private val socRecRelationshipsRepository: SocRecRelationshipsRepository,
) : InviteTrustedContactFlowUiStateMachine {
  @Composable
  override fun model(props: InviteTrustedContactFlowUiProps): ScreenModel {
    return addingTrustedContactUiStateMachine.model(
      props =
        AddingTrustedContactUiProps(
          account = props.account,
          onAddTc = { trustedContactAlias, hardwareProofOfPossession ->
            binding {
              val invitation =
                socRecRelationshipsRepository
                  .createInvitation(
                    account = props.account,
                    trustedContactAlias = trustedContactAlias,
                    hardwareProofOfPossession = hardwareProofOfPossession
                  ).bind()
              gettingStartedTaskDao.updateTask(
                id = GettingStartedTask.TaskId.InviteTrustedContact,
                state = GettingStartedTask.TaskState.Complete
              ).bind()
              invitation
            }
          },
          onInvitationShared = props.onExit,
          onExit = props.onExit
        )
    )
  }
}

private sealed interface UiState {
  data object AddingTrustedContact : UiState

  data object Failure : UiState
}
