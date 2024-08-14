package build.wallet.statemachine.recovery.socrec.list.full

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.UnendorsedTrustedContact
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiProps
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiStateMachine
import build.wallet.statemachine.recovery.socrec.view.ViewingInvitationProps
import build.wallet.statemachine.recovery.socrec.view.ViewingInvitationUiStateMachine
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerProps
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerUiStateMachine
import build.wallet.statemachine.recovery.socrec.view.ViewingRecoveryContactProps
import build.wallet.statemachine.recovery.socrec.view.ViewingRecoveryContactUiStateMachine
import kotlinx.datetime.Clock

class ListingTrustedContactsUiStateMachineImpl(
  private val viewingRecoveryContactUiStateMachine: ViewingRecoveryContactUiStateMachine,
  private val viewingInvitationUiStateMachine: ViewingInvitationUiStateMachine,
  private val viewingProtectedCustomerUiStateMachine: ViewingProtectedCustomerUiStateMachine,
  private val helpingWithRecoveryUiStateMachine: HelpingWithRecoveryUiStateMachine,
  private val clock: Clock,
) : ListingTrustedContactsUiStateMachine {
  @Composable
  override fun model(props: ListingTrustedContactsUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.ViewingListState) }

    val screenBody =
      TrustedContactsListBodyModel(
        onBackPressed = props.onExit,
        onAddPressed = props.onAddTCButtonPressed,
        onAcceptInvitePressed = props.onAcceptTrustedContactInvite,
        onContactPressed = {
          state =
            when (it) {
              is EndorsedTrustedContact -> State.ViewingTrustedContactDetailsState(it)
              is Invitation -> State.ViewingInvitationDetailsState(it)
              is UnendorsedTrustedContact -> error("TODO BKR-852")
            }
        },
        onProtectedCustomerPressed = { state = State.ViewingProtectedCustomerDetail(it) },
        contacts = props.relationships.endorsedTrustedContacts,
        invitations = props.relationships.invitations,
        protectedCustomers = props.relationships.protectedCustomers,
        now = clock.now().toEpochMilliseconds()
      )

    return when (val current = state) {
      is State.ViewingInvitationDetailsState ->
        viewingInvitationUiStateMachine.model(
          ViewingInvitationProps(
            hostScreen = ScreenModel(screenBody),
            invitation = current.invitation,
            fullAccount = props.account,
            onRefreshInvitation = props.socRecProtectedCustomerActions::refreshInvitation,
            onRemoveInvitation = props.socRecProtectedCustomerActions::removeTrustedContact,
            onExit = {
              state = State.ViewingListState
            }
          )
        )

      is State.ViewingTrustedContactDetailsState ->
        viewingRecoveryContactUiStateMachine.model(
          ViewingRecoveryContactProps(
            screenBody = screenBody,
            recoveryContact = current.endorsedTrustedContact,
            account = props.account,
            onRemoveContact = props.socRecProtectedCustomerActions::removeTrustedContact,
            afterContactRemoved = {
              state = State.ViewingListState
            },
            onExit = {
              state = State.ViewingListState
            }
          )
        )

      is State.ViewingProtectedCustomerDetail ->
        viewingProtectedCustomerUiStateMachine.model(
          ViewingProtectedCustomerProps(
            onExit = { state = State.ViewingListState },
            screenModel = screenBody.asRootScreen(),
            protectedCustomer = current.protectedCustomer,
            onHelpWithRecovery = {
              state =
                State.HelpingWithRecovery(
                  protectedCustomer = current.protectedCustomer
                )
            },
            onRemoveProtectedCustomer = {
              props.socRecProtectedCustomerActions.removeProtectedCustomer(current.protectedCustomer)
            }
          )
        )

      State.ViewingListState -> screenBody.asRootScreen()
      is State.HelpingWithRecovery ->
        helpingWithRecoveryUiStateMachine.model(
          props =
            HelpingWithRecoveryUiProps(
              account = props.account,
              protectedCustomer = current.protectedCustomer,
              onExit = { state = State.ViewingListState }
            )
        )
    }
  }

  sealed interface State {
    data object ViewingListState : State

    data class ViewingTrustedContactDetailsState(
      val endorsedTrustedContact: EndorsedTrustedContact,
    ) : State

    data class ViewingInvitationDetailsState(
      val invitation: Invitation,
    ) : State

    data class ViewingProtectedCustomerDetail(
      val protectedCustomer: ProtectedCustomer,
    ) : State

    data class HelpingWithRecovery(val protectedCustomer: ProtectedCustomer) : State
  }
}
