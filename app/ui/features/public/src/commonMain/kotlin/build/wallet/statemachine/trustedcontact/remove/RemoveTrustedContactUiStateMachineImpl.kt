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
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.HttpError
import build.wallet.relationships.RelationshipsService
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.*
import build.wallet.statemachine.recovery.RecoverySegment
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.datetime.Clock

@BitkeyInject(ActivityScope::class)
class RemoveTrustedContactUiStateMachineImpl(
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
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

    return when (val current = state) {
      is State.RemoveRequestState ->
        RemoveTrustedContactBodyModel(
          trustedContactAlias = props.trustedContact.trustedContactAlias,
          isExpiredInvitation = isExpiredInvitation,
          onRemove = {
            // For removing expired invitations we don't need to scan hardware
            state =
              if (isExpiredInvitation) {
                State.RemovingWithBitkeyState(proofOfPossession = null)
              } else {
                State.ScanningHardwareState
              }
          },
          onClosed = props.onClosed,
          isBeneficiary = TrustedContactRole.Beneficiary == props.trustedContact.roles.singleOrNull()
        ).asModalScreen()

      is State.ScanningHardwareState ->
        proofOfPossessionNfcStateMachine.model(
          ProofOfPossessionNfcProps(
            request =
              Request.HwKeyProof(
                onSuccess = { proof ->
                  state = State.RemovingWithBitkeyState(proof)
                }
              ),
            fullAccountId = props.account.accountId,
            onBack = {
              state = State.RemoveRequestState
            },
            screenPresentationStyle = ScreenPresentationStyle.Modal
          )
        )

      is State.RemovingWithBitkeyState -> {
        LaunchedEffect("remove-tc-with-bitkey") {
          relationshipsService.removeRelationship(
            account = props.account,
            hardwareProofOfPossession = current.proofOfPossession,
            authTokenScope = AuthTokenScope.Global,
            relationshipId = props.trustedContact.relationshipId
          ).onSuccess {
            props.onClosed()
          }.onFailure {
            state =
              State.FailedToRemoveState(
                proofOfPossession = current.proofOfPossession,
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
            actionDescription = "Removing Recovery Contact",
            cause = current.error
          ),
          onRetry = {
            state =
              State.RemovingWithBitkeyState(
                proofOfPossession = current.proofOfPossession
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

  /** Calling the server with established proof of possession */
  data class RemovingWithBitkeyState(
    val proofOfPossession: HwFactorProofOfPossession?,
  ) : State

  /** Error state */
  data class FailedToRemoveState(
    val proofOfPossession: HwFactorProofOfPossession?,
    val error: Error,
  ) : State
}
