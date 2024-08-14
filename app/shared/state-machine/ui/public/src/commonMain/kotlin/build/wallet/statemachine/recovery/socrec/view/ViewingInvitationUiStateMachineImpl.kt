package build.wallet.statemachine.recovery.socrec.view

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.logging.logFailure
import build.wallet.platform.sharing.SharingManager
import build.wallet.platform.sharing.shareInvitation
import build.wallet.recovery.socrec.InviteCodeLoader
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.recovery.socrec.reinvite.ReinviteTrustedContactUiProps
import build.wallet.statemachine.recovery.socrec.reinvite.ReinviteTrustedContactUiStateMachine
import build.wallet.statemachine.recovery.socrec.remove.RemoveTrustedContactUiProps
import build.wallet.statemachine.recovery.socrec.remove.RemoveTrustedContactUiStateMachine
import com.github.michaelbull.result.onSuccess
import kotlinx.datetime.Clock

class ViewingInvitationUiStateMachineImpl(
  private val removeTrustedContactsUiStateMachine: RemoveTrustedContactUiStateMachine,
  private val reinviteTrustedContactUiStateMachine: ReinviteTrustedContactUiStateMachine,
  private val sharingManager: SharingManager,
  private val clock: Clock,
  private val inviteCodeLoader: InviteCodeLoader,
) : ViewingInvitationUiStateMachine {
  @Composable
  override fun model(props: ViewingInvitationProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.Viewing) }
    var code: String by remember { mutableStateOf("") }

    LaunchedEffect(props.invitation.relationshipId) {
      inviteCodeLoader.getInviteCode(props.invitation)
        .logFailure { "failed to load invite code" }
        .onSuccess {
          code = it.inviteCode
        }
    }

    return when (state) {
      State.Viewing ->
        props.hostScreen.copy(
          bottomSheetModel =
            SheetModel(
              body =
                ViewingInvitationBodyModel(
                  invitation = props.invitation,
                  isExpired = props.invitation.isExpired(clock),
                  onRemove = {
                    state = State.Removing
                  },
                  onReinvite = {
                    state = State.Reinvite
                  },
                  onShare = {
                    sharingManager.shareInvitation(
                      code,
                      onCompletion = {
                        props.onExit()
                      }
                    )
                  },
                  onBack = props.onExit
                ),
              onClosed = props.onExit
            )
        )

      is State.Reinvite -> {
        reinviteTrustedContactUiStateMachine.model(
          ReinviteTrustedContactUiProps(
            account = props.fullAccount,
            trustedContactAlias = props.invitation.trustedContactAlias.alias,
            relationshipId = props.invitation.relationshipId,
            onReinviteTc = {
              props.onRefreshInvitation(props.invitation, it)
            },
            onExit = {
              props.onExit()
            }
          )
        )
      }

      is State.Removing -> {
        removeTrustedContactsUiStateMachine.model(
          RemoveTrustedContactUiProps(
            trustedContact = props.invitation,
            account = props.fullAccount,
            onRemoveTrustedContact = {
              props.onRemoveInvitation(props.invitation, it)
            },
            onClosed = {
              props.onExit()
            }
          )
        )
      }
    }
  }

  private sealed interface State {
    data object Viewing : State

    data object Reinvite : State

    data object Removing : State
  }
}
