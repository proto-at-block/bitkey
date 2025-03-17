package build.wallet.statemachine.trustedcontact.reinvite

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_BENEFICIARY_ENROLLMENT_REINVITE_FAILED
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_REINVITE_FAILED
import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.HttpError
import build.wallet.platform.clipboard.Clipboard
import build.wallet.platform.clipboard.plainTextItemAndroid
import build.wallet.platform.sharing.SharingManager
import build.wallet.platform.sharing.shareInvitation
import build.wallet.relationships.RelationshipsService
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.*
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.socrec.add.ShareInviteBodyModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class ReinviteTrustedContactUiStateMachineImpl(
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val sharingManager: SharingManager,
  private val clipboard: Clipboard,
  private val relationshipsService: RelationshipsService,
) : ReinviteTrustedContactUiStateMachine {
  @Composable
  override fun model(props: ReinviteTrustedContactUiProps): ScreenModel {
    var state: State by remember {
      mutableStateOf(State.SaveWithBitkeyRequestState(props.trustedContactAlias))
    }
    val scope = rememberStableCoroutineScope()

    return when (val current = state) {
      is State.SaveWithBitkeyRequestState ->
        ReinviteContactBodyModel(
          trustedContactName = current.tcName,
          isBeneficiary = props.isBeneficiary,
          onSave = {
            state = State.ScanningHardwareState(
              tcName = current.tcName
            )
          },
          onBackPressed = props.onExit
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
          relationshipsService.refreshInvitation(
            account = props.account,
            relationshipId = props.relationshipId,
            hardwareProofOfPossession = current.proofOfPossession
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
          eventTrackerScreenId = if (props.isBeneficiary) {
            TC_BENEFICIARY_ENROLLMENT_REINVITE_FAILED
          } else {
            TC_ENROLLMENT_REINVITE_FAILED
          },
          title = "Unable to save " + if (props.isBeneficiary) "beneficiary" else "trusted contact",
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
          isBeneficiary = props.isBeneficiary,
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
              isBeneficiary = props.isBeneficiary,
              inviteCode = current.invitation.inviteCode,
              onCompletion = {
                state = State.Success
              }
            )
          },
          onBackPressed = props.onSuccess // Complete flow without sharing, since invitation is already created
        ).asModalScreen()

      State.Success ->
        SuccessBodyModel(
          id = if (props.isBeneficiary) {
            SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_REINVITE_SENT
          } else {
            SocialRecoveryEventTrackerScreenId.TC_BENEFICIARY_ENROLLMENT_REINVITE_SENT
          },
          primaryButtonModel = ButtonDataModel("Got it", onClick = props.onSuccess),
          title = "You're all set",
          message = if (props.isBeneficiary) {
            """
             We'll let you know when your contact accepts their invite.
            """.trimIndent()
          } else {
            """
            You’ll get a notification when your Trusted Contact accepts your invite.
            
            You can manage your Trusted Contacts in your settings.
            """.trimIndent()
          }
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
