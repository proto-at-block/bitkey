package build.wallet.statemachine.recovery.socrec.reinvite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import build.wallet.bitkey.socrec.OutgoingInvitation
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.HttpError
import build.wallet.platform.clipboard.Clipboard
import build.wallet.platform.clipboard.plainTextItemAndroid
import build.wallet.platform.sharing.SharingManager
import build.wallet.platform.sharing.shareInvitation
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.NetworkErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.socrec.add.ShareInviteBodyModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class ReinviteTrustedContactUiStateMachineImpl(
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val sharingManager: SharingManager,
  private val clipboard: Clipboard,
) : ReinviteTrustedContactUiStateMachine {
  @Composable
  override fun model(props: ReinviteTrustedContactUiProps): ScreenModel {
    var state: State = State.SaveWithBitkeyRequestState(props.trustedContactAlias)
    val scope = rememberStableCoroutineScope()

    return when (val current = state) {
      is State.SaveWithBitkeyRequestState ->
        ReinviteContactBodyModel(
          trustedContactName = current.tcName,
          onSave = {
            state =
              State.ScanningHardwareState(
                tcName = current.tcName
              )
          },
          onBackPressed = {
            props.onExit()
          }
        ).asModalScreen()

      is State.ScanningHardwareState ->
        proofOfPossessionNfcStateMachine.model(
          ProofOfPossessionNfcProps(
            request =
              Request.HwKeyProof(
                onSuccess = { proof ->
                  state =
                    State.SavingWithBitkeyState(
                      proofOfPossession = proof,
                      tcName = current.tcName
                    )
                }
              ),
            fullAccountId = props.account.accountId,
            fullAccountConfig = props.account.keybox.config,
            onBack = {
              state =
                State.SaveWithBitkeyRequestState(
                  tcName = current.tcName
                )
            },
            screenPresentationStyle = ScreenPresentationStyle.Modal
          )
        )

      is State.SavingWithBitkeyState -> {
        LaunchedEffect("reinvite-tc-to-bitkey") {
          props.onReinviteTc(
            current.proofOfPossession
          )
            .onSuccess {
              state =
                State.ShareState(
                  invitation = it
                )
            }.onFailure {
              State.FailedToSaveState(
                proofOfPossession = current.proofOfPossession,
                error = it,
                tcName = current.tcName
              )
            }
        }.let {
          LoadingBodyModel(
            id = null
          ).asModalScreen()
        }
      }

      is State.FailedToSaveState ->
        NetworkErrorFormBodyModel(
          eventTrackerScreenId = null,
          title = "Unable to save contact",
          isConnectivityError = current.error is HttpError.NetworkError,
          onRetry = {
            state =
              State.SavingWithBitkeyState(
                proofOfPossession = current.proofOfPossession,
                tcName = current.tcName
              )
          },
          errorData = ErrorData(
            segment = RecoverySegment.SocRec.ProtectedCustomer.Setup,
            actionDescription = "Saving Re-invited Trusted contact to F8e",
            cause = current.error
          ),
          onBack = {
            state =
              State.SaveWithBitkeyRequestState(
                tcName = current.tcName
              )
          }
        ).asModalScreen()

      is State.ShareState ->
        ShareInviteBodyModel(
          trustedContactName = current.invitation.invitation.trustedContactAlias.alias,
          onShareComplete = {
            // We need to watch the clipboard on Android because we don't get
            // a callback from the share sheet when they use the copy action
            scope.launch {
              clipboard.plainTextItemAndroid().drop(1).collect { content ->
                content.let {
                  if (it.toString().contains(current.invitation.inviteCode)) {
                    state = State.Success
                  }
                }
              }
            }

            sharingManager.shareInvitation(
              inviteCode = current.invitation.inviteCode,
              onCompletion = {
                state = State.Success
              }
            )
          },
          onBackPressed = {
            // Complete flow without sharing, since invitation is already created:
            props.onExit()
          }
        ).asModalScreen()

      State.Success ->
        SuccessBodyModel(
          id = null,
          primaryButtonModel = ButtonDataModel("Got it", onClick = props.onExit),
          title = "You're all set.",
          message =
            """
            You’ll get a notification when your Trusted Contact accepts your invite.
            
            You can manage your Trusted Contacts in your settings.
            """.trimIndent()
        ).asModalScreen()
    }
  }

  private sealed interface State {
    data class SaveWithBitkeyRequestState(
      val tcName: String,
    ) : State

    data class ScanningHardwareState(
      val tcName: String,
    ) : State

    data class SavingWithBitkeyState(
      val proofOfPossession: HwFactorProofOfPossession,
      val tcName: String,
    ) : State

    data class FailedToSaveState(
      val proofOfPossession: HwFactorProofOfPossession,
      val error: Error,
      val tcName: String,
    ) : State

    data class ShareState(
      val invitation: OutgoingInvitation,
    ) : State

    data object Success : State
  }
}
