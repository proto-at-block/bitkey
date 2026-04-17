package build.wallet.statemachine.trustedcontact.reinvite

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_BENEFICIARY_ENROLLMENT_REINVITE_FAILED
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_REINVITE_FAILED
import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.ktor.result.HttpError
import build.wallet.platform.clipboard.Clipboard
import build.wallet.platform.clipboard.plainTextItemAndroid
import build.wallet.platform.sharing.SharingManager
import build.wallet.platform.sharing.shareInvitation
import build.wallet.relationships.RelationshipsService
import build.wallet.statemachine.auth.ActionProofType
import build.wallet.statemachine.auth.HardwareAuthUiProps
import build.wallet.statemachine.auth.HardwareAuthUiStateMachine
import build.wallet.statemachine.core.*
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.socrec.add.ShareInviteBodyModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class ReinviteTrustedContactUiStateMachineImpl(
  private val hardwareAuthUiStateMachine: HardwareAuthUiStateMachine,
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

      is State.ScanningHardwareState -> {
        val actionProofType = if (props.isBeneficiary) {
          ActionProofType.ReinviteBeneficiary(name = current.tcName, entityId = props.relationshipId)
        } else {
          ActionProofType.ReinviteRecoveryContact(name = current.tcName, entityId = props.relationshipId)
        }
        hardwareAuthUiStateMachine.model(
          HardwareAuthUiProps(
            account = props.account,
            actionProofType = actionProofType,
            segment = RecoverySegment.SocRec.ProtectedCustomer.Setup,
            actionDescription = "Reinviting trusted contact",
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            onSuccess = { proof ->
              state =
                State.SavingWithBitkeyState(
                  proof = proof,
                  tcName = current.tcName
                )
            },
            onBack = {
              state =
                State.SaveWithBitkeyRequestState(
                  tcName = current.tcName
                )
            }
          )
        )
      }

      is State.SavingWithBitkeyState -> {
        LaunchedEffect("reinvite-tc-to-bitkey") {
          relationshipsService.refreshInvitation(
            account = props.account,
            relationshipId = props.relationshipId,
            proof = current.proof
          )
            .onSuccess {
              state =
                State.ShareState(
                  invitation = it
                )
            }.onFailure {
              state = State.FailedToSaveState(
                proof = current.proof,
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
          title = "Unable to save " + if (props.isBeneficiary) "beneficiary" else "Recovery Contact",
          isConnectivityError = current.error is HttpError.NetworkError,
          onRetry = {
            state =
              State.SavingWithBitkeyState(
                proof = current.proof,
                tcName = current.tcName
              )
          },
          errorData = ErrorData(
            segment = RecoverySegment.SocRec.ProtectedCustomer.Setup,
            actionDescription = "Saving Re-invited Recovery Contact to F8e",
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
            SocialRecoveryEventTrackerScreenId.TC_BENEFICIARY_ENROLLMENT_REINVITE_SENT
          } else {
            SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_REINVITE_SENT
          },
          primaryButtonModel = ButtonDataModel("Got it", onClick = props.onSuccess),
          title = "You're all set",
          message = if (props.isBeneficiary) {
            """
             We'll let you know when your contact accepts their invite.
            """.trimIndent()
          } else {
            """
            You’ll get a notification when your Recovery Contact accepts your invite.
            
            You can manage your Recovery Contacts in Security Hub.
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
      val proof: PrivilegedActionProof,
      val tcName: String,
    ) : State

    data class FailedToSaveState(
      val proof: PrivilegedActionProof,
      val error: Error,
      val tcName: String,
    ) : State

    data class ShareState(
      val invitation: OutgoingInvitation,
    ) : State

    data object Success : State
  }
}
