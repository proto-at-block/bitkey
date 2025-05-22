package build.wallet.statemachine.inheritance.claims.start

import androidx.compose.runtime.*
import bitkey.notifications.NotificationsService
import build.wallet.analytics.events.screen.id.InheritanceEventTrackerScreenId
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.inheritance.InheritanceService
import build.wallet.statemachine.core.*
import build.wallet.statemachine.inheritance.InheritanceAppSegment
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelSettingsProps
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelSettingsUiStateMachine
import build.wallet.statemachine.settings.full.notifications.Source
import build.wallet.time.DateTimeFormatter
import build.wallet.time.TimeZoneProvider
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.datetime.toLocalDateTime

@BitkeyInject(ActivityScope::class)
class StartClaimUiStateMachineImpl(
  private val inheritanceService: InheritanceService,
  private val notificationChannelStateMachine: RecoveryChannelSettingsUiStateMachine,
  private val notificationsService: NotificationsService,
  private val dateTimeFormatter: DateTimeFormatter,
  private val timeZoneProvider: TimeZoneProvider,
) : StartClaimUiStateMachine {
  @Composable
  override fun model(props: StartClaimUiStateMachineProps): ScreenModel {
    var uiState: State by remember { mutableStateOf(State.EducationState) }
    val notificationPreferences = notificationsService.getCriticalNotificationStatus()
      .collectAsState(null)

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
      State.LoadingPermissions -> {
        when (val result = notificationPreferences.value) {
          null -> { // Stay on loading state
          }
          else -> uiState = nextPermissionsState(result)
        }
      }
      else -> {}
    }

    return when (val currentState = uiState) {
      State.EducationState -> StartClaimEducationBodyModel(
        onBack = props.onExit,
        onContinue = {
          uiState = nextPermissionsState(notificationPreferences.value)
        }
      ).asModalScreen()
      State.LoadingPermissions -> LoadingBodyModel(
        id = null,
        onBack = { uiState = State.EducationState }
      ).asModalScreen()
      is State.PermissionsLoadError -> ErrorFormBodyModel(
        eventTrackerScreenId = null,
        errorData = ErrorData(
          segment = InheritanceAppSegment.BeneficiaryClaim.Start,
          actionDescription = "Loading Permissions to before starting claim",
          cause = currentState.cause
        ),
        title = "Error Loading Notification Preferences",
        onBack = {
          uiState = State.EducationState
        },
        // Notifications are important, but shouldn't block this flow in an unexpected error,
        // Allow the user to skip:
        primaryButton = ButtonDataModel(
          text = "Skip",
          onClick = { uiState = State.ConfirmStartClaim() }
        ),
        secondaryButton = ButtonDataModel(
          text = "Cancel",
          onClick = { uiState = State.EducationState }
        )
      ).asModalScreen()
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
        completeTime = dateTimeFormatter.shortDateWithYear(
          localDateTime = currentState.claim.delayEndTime.toLocalDateTime(timeZoneProvider.current())
        ),
        onClose = props.onExit
      ).asModalScreen()
      State.PermissionsRequest -> notificationChannelStateMachine.model(
        props = RecoveryChannelSettingsProps(
          onContinue = {
            uiState = State.ConfirmStartClaim()
          },
          source = Source.InheritanceStartClaim,
          account = props.account,
          onBack = { uiState = State.EducationState }
        )
      ).copy(presentationStyle = ScreenPresentationStyle.Modal)
      is State.ClaimSubmissionFailed -> ClaimFailedBodyModel(
        error = currentState.error,
        tryAgain = { uiState = State.StartingClaim },
        cancel = props.onExit
      ).asModalScreen()
      State.StartingClaim -> LoadingBodyModel(
        id = InheritanceEventTrackerScreenId.SubmittingClaim,
        onBack = { uiState = State.EducationState }
      ).asModalScreen()
    }
  }

  private fun nextPermissionsState(
    notificationState: NotificationsService.NotificationStatus?,
  ): State {
    return when (notificationState) {
      NotificationsService.NotificationStatus.Enabled -> State.ConfirmStartClaim()
      is NotificationsService.NotificationStatus.Missing -> State.PermissionsRequest
      is NotificationsService.NotificationStatus.Error -> State.PermissionsLoadError(
        notificationState.cause
      )
      null -> State.LoadingPermissions
    }
  }

  sealed interface State {
    /**
     * Initial Education screen shown before starting a claim.
     */
    data object EducationState : State

    /**
     * Loading the notification permissions and preferences to determine
     * whether to prompt the user to set up critical alerts.
     */
    data object LoadingPermissions : State

    /**
     * An unexpected error occurred when loading notification state.
     */
    data class PermissionsLoadError(
      val cause: Throwable,
    ) : State

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
