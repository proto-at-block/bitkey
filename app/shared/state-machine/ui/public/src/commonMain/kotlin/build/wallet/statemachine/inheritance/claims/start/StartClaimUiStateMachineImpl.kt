package build.wallet.statemachine.inheritance.claims.start

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.PushNotificationEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inheritance.InheritanceService
import build.wallet.platform.permissions.Permission.PushNotifications
import build.wallet.platform.permissions.PermissionChecker
import build.wallet.platform.permissions.PermissionStatus.Authorized
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.RetreatStyle
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiProps
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachine
import build.wallet.statemachine.platform.permissions.NotificationRationale
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.datetime.toLocalDateTime

@BitkeyInject(ActivityScope::class)
class StartClaimUiStateMachineImpl(
  private val inheritanceService: InheritanceService,
  private val notificationsStateMachine: EnableNotificationsUiStateMachine,
  private val permissionChecker: PermissionChecker,
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
) : StartClaimUiStateMachine {
  @Composable
  override fun model(props: StartClaimUiStateMachineProps): ScreenModel {
    var uiState: State by remember { mutableStateOf(State.EducationState) }

    when (uiState) {
      State.StartingClaim -> LaunchedEffect("Submit Inheritance Claim") {
        inheritanceService.startInheritanceClaim(props.relationshipId)
          .onSuccess { claim ->
            uiState = State.ClaimStarted(claim)
          }
          .onFailure { error ->
            uiState = State.ClaimSubmissionFailed(error)
          }
      }
      else -> {}
    }

    return when (val currentState = uiState) {
      State.EducationState -> StartClaimEducationBodyModel(
        onBack = props.onExit,
        onContinue = {
          uiState = if (permissionChecker.getPermissionStatus(PushNotifications) != Authorized) {
            State.PermissionsRequest
          } else {
            State.ConfirmStartClaim()
          }
        }
      ).asModalFullScreen()
      is State.ConfirmStartClaim -> ScreenModel(
        body = StartClaimConfirmationBodyModel(
          onBack = { uiState = State.EducationState },
          onContinue = { uiState = currentState.copy(confirming = true) }
        ),
        bottomSheetModel =
          StartClaimConfirmationPromptBodyModel(
            onBack = { uiState = currentState.copy(confirming = false) },
            onConfirm = { uiState = State.StartingClaim }
          )
            .asSheetModalScreen { uiState = currentState.copy(confirming = false) }
            .takeIf { currentState.confirming },
        presentationStyle = ScreenPresentationStyle.ModalFullScreen
      )
      is State.ClaimStarted -> ClaimStartedBodyModel(
        completeTime = dateTimeFormatter.shortDate(
          localDateTime = currentState.claim.delayEndTime.toLocalDateTime(timeZoneProvider.current())
        ),
        onClose = props.onExit
      ).asModalFullScreen()
      State.PermissionsRequest -> notificationsStateMachine.model(
        EnableNotificationsUiProps(
          retreat = Retreat(
            style = RetreatStyle.Close,
            onRetreat = { uiState = State.ConfirmStartClaim() }
          ),
          onComplete = { uiState = State.ConfirmStartClaim() },
          eventTrackerContext = PushNotificationEventTrackerScreenIdContext.INHERITANCE_CLAIM,
          rationale = NotificationRationale.Generic
        )
      ).asModalFullScreen()
      is State.ClaimSubmissionFailed -> ClaimFailedBodyModel(
        error = currentState.error,
        tryAgain = { uiState = State.StartingClaim },
        cancel = props.onExit
      ).asModalFullScreen()
      State.StartingClaim -> LoadingBodyModel(
        id = InheritanceEventTrackerScreenId.SubmittingClaim,
        onBack = { uiState = State.EducationState }
      ).asModalFullScreen()
    }
  }

  sealed interface State {
    /**
     * Initial Education screen shown before starting a claim.
     */
    data object EducationState : State

    /**
     * Requesting Notification permissions before starting a claim.
     */
    data object PermissionsRequest : State

    /**
     * Prompt the user to confirm their intent to start a claim.
     *
     * This shows an initial explainer, before showing an additional
     * confirmation step in order to continue.
     */
    data class ConfirmStartClaim(
      val confirming: Boolean = false,
    ) : State

    /**
     * Attempting to start the claim with the server.
     */
    data object StartingClaim : State

    /**
     * The claim has been successfully started.
     */
    data class ClaimStarted(
      val claim: BeneficiaryClaim.PendingClaim,
    ) : State

    /**
     * Error screen shown when the claim failed to start.
     */
    data class ClaimSubmissionFailed(
      val error: Throwable,
    ) : State
  }
}
