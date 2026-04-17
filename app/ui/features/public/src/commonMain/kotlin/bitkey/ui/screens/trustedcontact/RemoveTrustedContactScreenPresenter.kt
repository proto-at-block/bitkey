package bitkey.ui.screens.trustedcontact

import androidx.compose.runtime.*
import bitkey.auth.AuthTokenScope
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.ktor.result.HttpError
import build.wallet.relationships.RelationshipsService
import build.wallet.statemachine.auth.ActionProofType
import build.wallet.statemachine.auth.HardwareAuthUiProps
import build.wallet.statemachine.auth.HardwareAuthUiStateMachine
import build.wallet.statemachine.core.*
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.trustedcontact.remove.RemoveTrustedContactBodyModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.datetime.Clock

data class RemoveTrustedContactScreen(
  val account: FullAccount,
  val trustedContact: TrustedContact,
  override val origin: Screen,
) : Screen

@BitkeyInject(ActivityScope::class)
class RemoveTrustedContactScreenPresenter(
  private val hardwareAuthUiStateMachine: HardwareAuthUiStateMachine,
  private val clock: Clock,
  private val relationshipsService: RelationshipsService,
) : ScreenPresenter<RemoveTrustedContactScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: RemoveTrustedContactScreen,
  ): ScreenModel {
    var state: State by remember { mutableStateOf(State.RemoveRequestState) }

    val isExpiredInvitation =
      if (screen.trustedContact is Invitation) {
        screen.trustedContact.isExpired(clock)
      } else {
        false
      }

    val isBeneficiary = screen.trustedContact.roles.contains(TrustedContactRole.Beneficiary)
    val actionDescription = if (isBeneficiary) "Removing Beneficiary" else "Removing Recovery Contact"

    return when (val current = state) {
      is State.RemoveRequestState ->
        RemoveTrustedContactBodyModel(
          trustedContactAlias = screen.trustedContact.trustedContactAlias,
          isExpiredInvitation = isExpiredInvitation,
          onRemove = {
            // For removing expired invitations we don't need to scan hardware
            state =
              if (isExpiredInvitation) {
                State.RemovingState(proof = null)
              } else {
                State.ScanningHardwareState
              }
          },
          onClosed = {
            navigator.goTo(screen.origin)
          },
          isBeneficiary = isBeneficiary
        ).asModalScreen()

      is State.ScanningHardwareState ->
        hardwareAuthUiStateMachine.model(
          HardwareAuthUiProps(
            account = screen.account,
            actionProofType = if (isBeneficiary) {
              ActionProofType.RemoveBeneficiary(entityId = screen.trustedContact.relationshipId, name = screen.trustedContact.trustedContactAlias.alias)
            } else {
              ActionProofType.RemoveRecoveryContact(entityId = screen.trustedContact.relationshipId, name = screen.trustedContact.trustedContactAlias.alias)
            },
            segment = RecoverySegment.SocRec.ProtectedCustomer.Setup,
            actionDescription = actionDescription,
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            onSuccess = { proof ->
              state = State.RemovingState(proof)
            },
            onBack = {
              state = State.RemoveRequestState
            }
          )
        )

      is State.RemovingState -> {
        LaunchedEffect("remove-tc-with-bitkey") {
          relationshipsService.removeRelationship(
            account = screen.account,
            proof = current.proof,
            authTokenScope = AuthTokenScope.Global,
            relationshipId = screen.trustedContact.relationshipId
          ).onSuccess {
            navigator.goTo(screen.origin)
          }.onFailure {
            state =
              State.FailedToRemoveState(
                proof = current.proof,
                error = it
              )
          }
        }
        LoadingBodyModel(id = SocialRecoveryEventTrackerScreenId.TC_MANAGEMENT_REMOVAL_LOADING).asModalScreen()
      }

      is State.FailedToRemoveState ->
        NetworkErrorFormBodyModel(
          eventTrackerScreenId = SocialRecoveryEventTrackerScreenId.TC_MANAGEMENT_REMOVAL_FAILED,
          title = "Unable to remove contact",
          isConnectivityError = current.error is HttpError.NetworkError,
          errorData = ErrorData(
            segment = RecoverySegment.SocRec.ProtectedCustomer.Setup,
            actionDescription = actionDescription,
            cause = current.error
          ),
          onRetry = {
            state =
              State.RemovingState(
                proof = current.proof
              )
          },
          onBack = {
            state =
              State.RemoveRequestState
          }
        ).asModalScreen()
    }
  }
}

// TODO W-11228 convert these states to navigator pattern
private sealed interface State {
  /** Initial sheet state, user has not yet confirmed removal */
  data object RemoveRequestState : State

  /** Scanning hardware for proof of possession */
  data object ScanningHardwareState : State

  /** Calling the server with established proof */
  data class RemovingState(
    val proof: PrivilegedActionProof?,
  ) : State

  /** Error state */
  data class FailedToRemoveState(
    val proof: PrivilegedActionProof?,
    val error: Error,
  ) : State
}
