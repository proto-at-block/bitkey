package build.wallet.statemachine.recovery.socrec.add

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.bitkey.relationships.TrustedContactAlias
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
import build.wallet.statemachine.core.input.NameInputBodyModel
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiStateMachineImpl.State.*
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import com.github.michaelbull.result.mapBoth
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class AddingTrustedContactUiStateMachineImpl(
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val sharingManager: SharingManager,
  private val clipboard: Clipboard,
  private val eventTracker: EventTracker,
) : AddingTrustedContactUiStateMachine {
  @Composable
  override fun model(props: AddingTrustedContactUiProps): ScreenModel {
    val isInheritance = props.trustedContactRole == TrustedContactRole.Beneficiary
    var state: State by remember { mutableStateOf(EnterTcNameState()) }
    val scope = rememberStableCoroutineScope()
    val contactType = if (isInheritance) "beneficiary" else "trusted contact"

    return when (val current = state) {
      is EnterTcNameState -> {
        var input by remember { mutableStateOf(current.tcNameInitial) }
        val continueClick = remember(input) {
          if (input.isNotBlank()) {
            StandardClick {
              state = SaveWithBitkeyRequestState(tcName = input)
            }
          } else {
            StandardClick { } // noop
          }
        }

        NameInputBodyModel(
          id = SocialRecoveryEventTrackerScreenId.TC_ADD_TC_NAME,
          title = "Add your $contactType's name",
          subline = "Add a name, or nickname, to help you recognize your $contactType in the app.",
          value = input,
          primaryButton = ButtonModel(
            text = "Continue",
            isEnabled = input.isNotBlank(),
            onClick = continueClick,
            size = ButtonModel.Size.Footer
          ),
          onValueChange = { input = it },
          onClose = props.onExit
        ).asModalScreen()
      }

      is SaveWithBitkeyRequestState ->
        SaveContactBodyModel(
          trustedContactName = current.tcName,
          isBeneficiary = isInheritance,
          onSave = {
            state =
              ScanningHardwareState(
                tcName = current.tcName
              )
          },
          onBackPressed = {
            state =
              EnterTcNameState(
                tcNameInitial = current.tcName
              )
          }
        ).asModalScreen()

      is ScanningHardwareState ->
        proofOfPossessionNfcStateMachine.model(
          ProofOfPossessionNfcProps(
            request =
              Request.HwKeyProof(
                onSuccess = { proof ->
                  state =
                    SavingWithBitkeyState(
                      proofOfPossession = proof,
                      tcName = current.tcName
                    )
                }
              ),
            fullAccountId = props.account.accountId,
            fullAccountConfig = props.account.keybox.config,
            onBack = {
              state =
                SaveWithBitkeyRequestState(
                  tcName = current.tcName
                )
            },
            screenPresentationStyle = ScreenPresentationStyle.Modal
          )
        )

      is SavingWithBitkeyState -> {
        LaunchedEffect("save-tc-to-bitkey") {
          val result =
            props.onAddTc(
              TrustedContactAlias(current.tcName),
              current.proofOfPossession
            )
          state =
            result.mapBoth(
              success = {
                ShareState(
                  invitation = result.value
                )
              },
              failure = {
                FailedToSaveState(
                  proofOfPossession = current.proofOfPossession,
                  error = result.error,
                  tcName = current.tcName
                )
              }
            )
        }.let {
          LoadingBodyModel(
            id = null
          ).asModalScreen()
        }
      }

      is FailedToSaveState ->
        NetworkErrorFormBodyModel(
          eventTrackerScreenId = null,
          title = "Unable to save contact",
          isConnectivityError = current.error is HttpError.NetworkError,
          errorData = ErrorData(
            segment = RecoverySegment.SocRec.ProtectedCustomer.Setup,
            actionDescription = "Saving Trusted contact to F8e",
            cause = current.error
          ),
          onRetry = {
            state =
              SavingWithBitkeyState(
                proofOfPossession = current.proofOfPossession,
                tcName = current.tcName
              )
          },
          onBack = {
            state =
              SaveWithBitkeyRequestState(
                tcName = current.tcName
              )
          }
        ).asModalScreen()

      is ShareState ->
        ShareInviteBodyModel(
          trustedContactName = current.invitation.invitation.trustedContactAlias.alias,
          isBeneficiary = isInheritance,
          onShareComplete = {
            // We need to watch the clipboard on Android because we don't get
            // a callback from the share sheet when they use the copy action
            scope.launch {
              clipboard.plainTextItemAndroid().drop(1).collect { content ->
                content.let {
                  if (it.toString().contains(current.invitation.inviteCode)) {
                    state = Success
                  }
                }
              }
            }

            sharingManager.shareInvitation(
              inviteCode = current.invitation.inviteCode,
              isBeneficiary = isInheritance,
              onCompletion = {
                state = Success
              }, onFailure = {
                eventTracker.track(Action.ACTION_APP_SOCREC_TC_INVITE_DISMISSED_SHEET_WITHOUT_SHARING)
              }
            )
          },
          onBackPressed = {
            // Complete flow without sharing, since invitation is already created:
            props.onInvitationShared()
          }
        ).asModalScreen()

      Success -> {
        SuccessBodyModel(
          id = SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_SUCCESS,
          primaryButtonModel = ButtonDataModel("Got it", onClick = props.onInvitationShared),
          title = "You're all set.",
          message = if (isInheritance) {
            "We'll let you know when your contact accepts their invite."
          } else {
            "You can manage your trusted contacts in your settings."
          }
        ).asModalScreen()
      }
    }
  }

  private sealed interface State {
    data class EnterTcNameState(
      val tcNameInitial: String = "",
    ) : State

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
