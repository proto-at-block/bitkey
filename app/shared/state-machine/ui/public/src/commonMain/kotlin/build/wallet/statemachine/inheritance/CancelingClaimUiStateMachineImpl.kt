package build.wallet.statemachine.inheritance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.bitkey.relationships.RelationshipId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inheritance.InheritanceService
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.*
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class CancelingClaimUiStateMachineImpl(
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val inheritanceService: InheritanceService,
) : CancelingClaimUiStateMachine {
  val headline = "Cancel Inheritance Claim?"
  val subline = "This action cannot be undone."
  val primaryButtonText = "Cancel Inheritance Claim"

  @Composable
  override fun model(props: CancelingClaimUiProps): ScreenModel {
    var state: State by remember {
      mutableStateOf(State.ConfirmingClaimCancelation(props.relationshipId))
    }

    return when (val current = state) {
      is State.ConfirmingClaimCancelation -> {
        ScreenModel(
          body = props.body,
          bottomSheetModel = SheetModel(
            body = DestructiveInheritanceActionBodyModel(
              headline = headline,
              subline = subline,
              primaryButtonText = primaryButtonText,
              onClose = props.onExit,
              onPrimaryClick = { state = State.ScanningToCancelClaim(props.relationshipId) }
            ),
            onClosed = props.onExit
          )
        )
      }

      is State.ScanningToCancelClaim -> {
        proofOfPossessionNfcStateMachine.model(
          ProofOfPossessionNfcProps(
            request =
              Request.HwKeyProof(
                onSuccess = { proof ->
                  state =
                    State.CancelingClaim(
                      relationshipId = current.relationshipId
                    )
                }
              ),
            fullAccountId = props.account.accountId,
            fullAccountConfig = props.account.keybox.config,
            onBack = {
              state =
                State.ConfirmingClaimCancelation(
                  relationshipId = current.relationshipId
                )
            },
            screenPresentationStyle = ScreenPresentationStyle.Modal
          )
        )
      }

      is State.CancelingClaim -> {
        LaunchedEffect("cancel-claim") {
          inheritanceService.cancelClaims(
            current.relationshipId
          ).onSuccess {
            props.onSuccess()
          }.onFailure { error ->
            state = State.CancelingClaimFailed(error)
          }
        }

        ScreenModel(
          body = props.body,
          bottomSheetModel = SheetModel(
            body = DestructiveInheritanceActionBodyModel(
              headline = headline,
              subline = subline,
              primaryButtonText = primaryButtonText,
              onClose = props.onExit,
              onPrimaryClick = { },
              isLoading = true
            ),
            onClosed = props.onExit
          )
        )
      }

      is State.CancelingClaimFailed ->
        ScreenModel(
          body = ErrorFormBodyModel(
            title = "Failed To Cancel Claim",
            subline = "Please check your internet connection and try again.",
            primaryButton = ButtonDataModel(
              text = "Okay",
              onClick = props.onExit
            ),
            errorData = ErrorData(
              segment = InheritanceAppSegment.BeneficiaryClaim.Cancel,
              cause = current.error,
              actionDescription = "Canceling inheritance claim"
            ),
            eventTrackerScreenId = InheritanceEventTrackerScreenId.ManageInheritance
          )
        )
    }
  }

  private sealed interface State {
    data class CancelingClaimFailed(
      val error: Throwable,
    ) : State

    data class ScanningToCancelClaim(
      val relationshipId: RelationshipId,
    ) : State

    data class ConfirmingClaimCancelation(
      val relationshipId: RelationshipId,
    ) : State

    data class CancelingClaim(
      val relationshipId: RelationshipId,
    ) : State
  }
}
