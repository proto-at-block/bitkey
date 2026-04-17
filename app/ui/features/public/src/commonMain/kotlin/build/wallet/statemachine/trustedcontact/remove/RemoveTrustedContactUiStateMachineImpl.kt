package build.wallet.statemachine.trustedcontact.remove

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import bitkey.auth.AuthTokenScope
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.relationships.Invitation
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
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.datetime.Clock

@BitkeyInject(ActivityScope::class)
class RemoveTrustedContactUiStateMachineImpl(
  private val hardwareAuthUiStateMachine: HardwareAuthUiStateMachine,
  private val clock: Clock,
  private val relationshipsService: RelationshipsService,
) : RemoveTrustedContactUiStateMachine {
  @Composable
  override fun model(props: RemoveTrustedContactUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.RemoveRequestState) }

    val isExpiredInvitation =
      if (props.trustedContact is Invitation) {
        props.trustedContact.isExpired(clock)
      } else {
        false
      }

    val isBeneficiary = props.trustedContact.roles.contains(TrustedContactRole.Beneficiary)
    val actionDescription = if (isBeneficiary) "Removing Beneficiary" else "Removing Recovery Contact"

    return when (val current = state) {
      is State.RemoveRequestState ->
        RemoveTrustedContactBodyModel(
          trustedContactAlias = props.trustedContact.trustedContactAlias,
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
          onClosed = props.onClosed,
          isBeneficiary = isBeneficiary
        ).asModalScreen()

      is State.ScanningHardwareState ->
        hardwareAuthUiStateMachine.model(
          HardwareAuthUiProps(
            account = props.account,
            actionProofType = if (isBeneficiary) {
              ActionProofType.RemoveBeneficiary(entityId = props.trustedContact.relationshipId, name = props.trustedContact.trustedContactAlias.alias)
            } else {
              ActionProofType.RemoveRecoveryContact(entityId = props.trustedContact.relationshipId, name = props.trustedContact.trustedContactAlias.alias)
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
            account = props.account,
            proof = current.proof,
            authTokenScope = AuthTokenScope.Global,
            relationshipId = props.trustedContact.relationshipId
          ).onSuccess {
            props.onClosed()
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
