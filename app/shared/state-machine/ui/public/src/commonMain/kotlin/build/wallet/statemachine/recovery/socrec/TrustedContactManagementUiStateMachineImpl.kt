package build.wallet.statemachine.recovery.socrec

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.RetreatStyle
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementUiStateMachineImpl.State.AddingTrustedContactState
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementUiStateMachineImpl.State.ListingContactsState
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiProps
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiStateMachine
import build.wallet.statemachine.recovery.socrec.list.full.ListingTrustedContactsUiProps
import build.wallet.statemachine.recovery.socrec.list.full.ListingTrustedContactsUiStateMachine
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine

class TrustedContactManagementUiStateMachineImpl(
  private val listingTrustedContactsUiStateMachine: ListingTrustedContactsUiStateMachine,
  private val addingTrustedContactUiStateMachine: AddingTrustedContactUiStateMachine,
  private val trustedContactEnrollmentUiStateMachine: TrustedContactEnrollmentUiStateMachine,
) : TrustedContactManagementUiStateMachine {
  @Composable
  override fun model(props: TrustedContactManagementProps): ScreenModel {
    var state: State by remember { mutableStateOf(ListingContactsState) }
    val actions = props.socRecActions

    return when (state) {
      ListingContactsState ->
        listingTrustedContactsUiStateMachine.model(
          ListingTrustedContactsUiProps(
            account = props.account,
            relationships = props.socRecRelationships,
            socRecFullAccountActions = actions,
            onAddTCButtonPressed = {
              state = AddingTrustedContactState
            },
            onAcceptTrustedContactInvite = { state = State.EnrollingAsTrustedContact },
            onExit = props.onExit
          )
        )

      AddingTrustedContactState ->
        addingTrustedContactUiStateMachine.model(
          AddingTrustedContactUiProps(
            account = props.account,
            onAddTc = actions::createInvitation,
            onInvitationShared = {
              state = ListingContactsState
            },
            onExit = {
              state = ListingContactsState
            }
          )
        )

      State.EnrollingAsTrustedContact ->
        trustedContactEnrollmentUiStateMachine.model(
          TrustedContactEnrollmentUiProps(
            retreat =
              Retreat(
                style = RetreatStyle.Close,
                onRetreat = { state = ListingContactsState }
              ),
            account = props.account,
            acceptInvitation = actions::acceptInvitation,
            retrieveInvitation = actions::retrieveInvitation,
            inviteCode = props.inviteCode,
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            onDone = { state = ListingContactsState }
          )
        )
    }
  }

  private sealed interface State {
    data object ListingContactsState : State

    data object AddingTrustedContactState : State

    data object EnrollingAsTrustedContact : State
  }
}
