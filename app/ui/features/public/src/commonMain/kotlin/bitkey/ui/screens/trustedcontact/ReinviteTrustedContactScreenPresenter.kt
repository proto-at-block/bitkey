package bitkey.ui.screens.trustedcontact

import androidx.compose.runtime.*
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_BENEFICIARY_ENROLLMENT_REINVITE_FAILED
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_REINVITE_FAILED
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.bitkey.relationships.TrustedContactRole
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
import build.wallet.statemachine.trustedcontact.reinvite.ReinviteContactBodyModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class ReinviteTrustedContactScreen(
  val account: FullAccount,
  val invitation: Invitation,
  override val origin: Screen,
) : Screen

@BitkeyInject(ActivityScope::class)
class ReinviteTrustedContactScreenPresenter(
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val sharingManager: SharingManager,
  private val clipboard: Clipboard,
  private val relationshipsService: RelationshipsService,
) : ScreenPresenter<ReinviteTrustedContactScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: ReinviteTrustedContactScreen,
  ): ScreenModel {
    var state: State by remember {
      mutableStateOf(State.SaveWithBitkeyRequestState(screen.invitation.trustedContactAlias.alias))
    }
    val scope = rememberStableCoroutineScope()

    val isBeneficiary by remember(screen.invitation) {
      mutableStateOf(screen.invitation.roles.contains(TrustedContactRole.Beneficiary))
    }

    return when (val current = state) {
      is State.SaveWithBitkeyRequestState ->
        ReinviteContactBodyModel(
          trustedContactName = current.tcName,
          isBeneficiary = isBeneficiary,
          onSave = {
            state = State.ScanningHardwareState(
              tcName = current.tcName
            )
          },
          onBackPressed = { navigator.goTo(screen.origin) }
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
            fullAccountId = screen.account.accountId,
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
            account = screen.account,
            relationshipId = screen.invitation.relationshipId,
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
          eventTrackerScreenId = if (isBeneficiary) {
            TC_BENEFICIARY_ENROLLMENT_REINVITE_FAILED
          } else {
            TC_ENROLLMENT_REINVITE_FAILED
          },
          title = "Unable to save " + if (isBeneficiary) "beneficiary" else "Recovery Contact",
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
          isBeneficiary = isBeneficiary,
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
              isBeneficiary = isBeneficiary,
              inviteCode = current.invitation.inviteCode,
              onCompletion = {
                state = State.Success
              }
            )
          },
          onBackPressed = {
            // Complete flow without sharing, since invitation is already created
            navigator.goTo(screen.origin)
          }
        ).asModalScreen()

      State.Success ->
        SuccessBodyModel(
          id = if (isBeneficiary) {
            SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_REINVITE_SENT
          } else {
            SocialRecoveryEventTrackerScreenId.TC_BENEFICIARY_ENROLLMENT_REINVITE_SENT
          },
          primaryButtonModel = ButtonDataModel("Got it", onClick = { navigator.goTo(screen.origin) }),
          title = "You're all set",
          message = if (isBeneficiary) {
            """
             We'll let you know when your contact accepts their invite.
            """.trimIndent()
          } else {
            """
            You’ll get a notification when your Recovery Contact accepts your invite.
            
            You can manage your Recovery Contacts in your settings.
            """.trimIndent()
          }
        ).asModalScreen()
    }
  }

  // TODO W-11228 convert these states to navigator pattern
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
