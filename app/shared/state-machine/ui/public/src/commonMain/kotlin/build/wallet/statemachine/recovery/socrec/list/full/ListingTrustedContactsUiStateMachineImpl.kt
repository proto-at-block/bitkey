package build.wallet.statemachine.recovery.socrec.list.full

import androidx.compose.runtime.*
import build.wallet.bitkey.relationships.EndorsedTrustedContact
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.bitkey.relationships.UnendorsedTrustedContact
import build.wallet.recovery.socrec.SocRecService
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiProps
import build.wallet.statemachine.recovery.socrec.help.HelpingWithRecoveryUiStateMachine
import build.wallet.statemachine.recovery.socrec.view.*
import kotlinx.datetime.Clock

class ListingTrustedContactsUiStateMachineImpl(
  private val viewingRecoveryContactUiStateMachine: ViewingRecoveryContactUiStateMachine,
  private val viewingInvitationUiStateMachine: ViewingInvitationUiStateMachine,
  private val viewingProtectedCustomerUiStateMachine: ViewingProtectedCustomerUiStateMachine,
  private val helpingWithRecoveryUiStateMachine: HelpingWithRecoveryUiStateMachine,
  private val clock: Clock,
  private val socRecService: SocRecService,
) : ListingTrustedContactsUiStateMachine {
  @Composable
  override fun model(props: ListingTrustedContactsUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.ViewingListState) }

    val socRecRelationships by remember { socRecService.socRecRelationships }
      .collectAsState()

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
        contacts = socRecRelationships?.endorsedTrustedContacts ?: emptyList(),
        invitations = socRecRelationships?.invitations ?: emptyList(),
        protectedCustomers = socRecRelationships?.protectedCustomers ?: emptyList(),
        now = clock.now().toEpochMilliseconds()
      )

    return when (val current = state) {
      is State.ViewingInvitationDetailsState ->
        viewingInvitationUiStateMachine.model(
          ViewingInvitationProps(
            hostScreen = ScreenModel(screenBody),
            invitation = current.invitation,
            fullAccount = props.account,
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
            account = props.account,
            onExit = { state = State.ViewingListState },
            screenModel = screenBody.asRootScreen(),
            protectedCustomer = current.protectedCustomer,
            onHelpWithRecovery = {
              state =
                State.HelpingWithRecovery(
                  protectedCustomer = current.protectedCustomer
                )
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
