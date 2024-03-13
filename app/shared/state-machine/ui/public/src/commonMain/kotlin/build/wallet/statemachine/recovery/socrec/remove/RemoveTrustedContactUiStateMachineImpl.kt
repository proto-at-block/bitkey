package build.wallet.statemachine.recovery.socrec.remove

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.socrec.Invitation
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.HttpError
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.NetworkErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.datetime.Clock

class RemoveTrustedContactUiStateMachineImpl(
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val clock: Clock,
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
          onClosed = props.onClosed
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
            fullAccountConfig = props.account.keybox.config,
            onBack = {
              state = State.RemoveRequestState
            },
            screenPresentationStyle = ScreenPresentationStyle.Modal
          )
        )

      is State.RemovingWithBitkeyState -> {
        LaunchedEffect("remove-tc-with-bitkey") {
          props.onRemoveTrustedContact(
            current.proofOfPossession
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
