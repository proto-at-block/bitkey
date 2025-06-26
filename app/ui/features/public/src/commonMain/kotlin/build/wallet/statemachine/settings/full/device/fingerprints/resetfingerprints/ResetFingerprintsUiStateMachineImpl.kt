package build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints

import androidx.compose.runtime.*
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.privilegedactions.FingerprintResetService
import bitkey.privilegedactions.PrivilegedActionError
import build.wallet.Progress
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.coroutines.flow.launchTicker
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.grants.GrantAction
import build.wallet.logging.logError
import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.time.DurationFormatter
import build.wallet.time.durationProgress
import build.wallet.time.nonNegativeDurationBetween
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

@BitkeyInject(ActivityScope::class)
class ResetFingerprintsUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val clock: Clock,
  private val durationFormatter: DurationFormatter,
  private val fingerprintResetService: FingerprintResetService,
) : ResetFingerprintsUiStateMachine {
  @Composable
  override fun model(props: ResetFingerprintsProps): ScreenModel {
    var uiState: ResetFingerprintsUiState by remember(props.initialPendingActionInfo) {
      val initialState = props.initialPendingActionInfo?.let {
        ResetFingerprintsUiState.ShowingProgress(
          actionId = it.actionId,
          startTime = it.startTime,
          endTime = it.endTime,
          completionToken = it.completionToken,
          cancellationToken = it.cancellationToken
        )
      } ?: ResetFingerprintsUiState.Loading
      mutableStateOf(initialState)
    }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(props.initialPendingActionInfo == null && uiState == ResetFingerprintsUiState.Loading) {
      if (uiState == ResetFingerprintsUiState.Loading) {
        coroutineScope.launch {
          fingerprintResetService.getLatestFingerprintResetAction()
            .onSuccess { pendingAction: PrivilegedActionInstance? ->
              if (pendingAction != null && pendingAction.authorizationStrategy is AuthorizationStrategy.DelayAndNotify) {
                val authStrategy = pendingAction.authorizationStrategy as AuthorizationStrategy.DelayAndNotify
                // TODO: remove this when we get delay_start_time from f8e
                val effectiveStartTime = authStrategy.delayEndTime - Duration.parse("7d")

                uiState = ResetFingerprintsUiState.ShowingProgress(
                  actionId = pendingAction.id,
                  startTime = effectiveStartTime,
                  endTime = authStrategy.delayEndTime,
                  completionToken = authStrategy.completionToken,
                  cancellationToken = authStrategy.cancellationToken
                )
              } else {
                uiState = ResetFingerprintsUiState.ShowingConfirmation()
              }
            }
            .onFailure { error ->
              logError { "Failed to get latest fingerprint reset action: $error" }
              uiState = ResetFingerprintsUiState.ShowingConfirmation()
            }
        }
      }
    }

    return when (val state = uiState) {
      is ResetFingerprintsUiState.Loading -> {
        ScreenModel(
          body = LoadingBodyModel(
            id = ResetFingerprintsEventTrackerScreenId.LOADING_FINGERPRINT_RESET_STATUS,
            message = "Loading fingerprint reset status...",
            onBack = props.onCancel
          ),
          presentationStyle = ScreenPresentationStyle.Modal
        )
      }

      is ResetFingerprintsUiState.ShowingConfirmation ->
        handleShowingConfirmation(state, props) { newState -> uiState = newState }

      is ResetFingerprintsUiState.ReadyForNfcScan ->
        handleReadyForNfcScan(coroutineScope) { newState -> uiState = newState }

      is ResetFingerprintsUiState.ShowingProgress ->
        handleShowingProgress(state, props) { newState -> uiState = newState }

      is ResetFingerprintsUiState.Finishing ->
        handleFinishing(state, props) { newState -> uiState = newState }

      is ResetFingerprintsUiState.Cancelling ->
        handleCancelling(state, props) { newState -> uiState = newState }

      is ResetFingerprintsUiState.Error ->
        handleError(state, props) { newState -> uiState = newState }
    }
  }

  @Composable
  private fun handleShowingConfirmation(
    state: ResetFingerprintsUiState.ShowingConfirmation,
    props: ResetFingerprintsProps,
    updateState: (ResetFingerprintsUiState) -> Unit,
  ): ScreenModel {
    return ScreenModel(
      body = ResetFingerprintsConfirmationBodyModel(
        onClose = props.onCancel,
        onConfirmReset = {
          val onDismiss = {
            updateState(ResetFingerprintsUiState.ShowingConfirmation())
          }
          updateState(
            state.copy(
              bottomSheetModel = ResetFingerprintsConfirmationSheetModel(
                onDismiss = onDismiss,
                onConfirmReset = {
                  updateState(ResetFingerprintsUiState.ReadyForNfcScan)
                }
              ).asSheetModalScreen(onClosed = onDismiss)
            )
          )
        }
      ),
      bottomSheetModel = state.bottomSheetModel,
      presentationStyle = ScreenPresentationStyle.Modal
    )
  }

  @Composable
  private fun handleReadyForNfcScan(
    coroutineScope: CoroutineScope,
    updateState: (ResetFingerprintsUiState) -> Unit,
  ): ScreenModel {
    return nfcSessionUIStateMachine.model(
      props = NfcSessionUIStateMachineProps<ResetFingerprintsNfcResult>(
        session = { session, commands ->
          val grantRequest = commands.getGrantRequest(session, GrantAction.FINGERPRINT_RESET)
          ResetFingerprintsNfcResult.GrantRequestRetrieved(grantRequest)
        },
        onSuccess = { result ->
          when (result) {
            is ResetFingerprintsNfcResult.GrantRequestRetrieved -> {
              coroutineScope.launch {
                val serviceResult =
                  fingerprintResetService.createFingerprintResetPrivilegedAction(
                    grantRequest = result.grantRequest
                  )

                serviceResult
                  .onSuccess { actionInstance ->
                    val startTime = clock.now()
                    val authStrategy = actionInstance.authorizationStrategy as AuthorizationStrategy.DelayAndNotify
                    updateState(
                      ResetFingerprintsUiState.ShowingProgress(
                        actionId = actionInstance.id,
                        startTime = startTime,
                        endTime = authStrategy.delayEndTime,
                        completionToken = authStrategy.completionToken,
                        cancellationToken = authStrategy.cancellationToken
                      )
                    )
                  }
                  .onFailure { error ->
                    logError { "Failed to create fingerprint reset privileged action: $error" }
                    updateState(ResetFingerprintsUiState.Error.CreatePrivilegedActionError(error))
                  }
              }
            }
            else -> {
              updateState(
                ResetFingerprintsUiState.Error.GenericError(
                  title = "NFC Operation Failed",
                  message = "The NFC operation failed or returned an unexpected result. Please try again.",
                  cause = RuntimeException("NFC operation returned unexpected result: $result after grant request retrieval."),
                  eventTrackerScreenId = ResetFingerprintsEventTrackerScreenId.ERROR_NFC_OPERATION_FAILED
                )
              )
            }
          }
        },
        onCancel = {
          updateState(ResetFingerprintsUiState.ShowingConfirmation())
        },
        screenPresentationStyle = ScreenPresentationStyle.Modal,
        eventTrackerContext = NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS,
        needsAuthentication = false,
        hardwareVerification = NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
      )
    )
  }

  @Composable
  private fun handleShowingProgress(
    state: ResetFingerprintsUiState.ShowingProgress,
    props: ResetFingerprintsProps,
    updateState: (ResetFingerprintsUiState) -> Unit,
  ): ScreenModel {
    var remainingDelayPeriod by remember {
      mutableStateOf(nonNegativeDurationBetween(clock.now(), state.endTime))
    }
    val formattedDuration by remember(remainingDelayPeriod) {
      derivedStateOf { durationFormatter.formatWithWords(remainingDelayPeriod) }
    }
    val progress by remember(remainingDelayPeriod) {
      derivedStateOf {
        durationProgress(
          now = clock.now(),
          startTime = state.startTime,
          endTime = state.endTime
        ).getOrElse { Progress.Zero }
      }
    }

    LaunchedEffect("update-reset-progress") {
      launchTicker(DurationFormatter.MINIMUM_DURATION_WORD_FORMAT_UPDATE) {
        remainingDelayPeriod = nonNegativeDurationBetween(clock.now(), state.endTime)
        if (remainingDelayPeriod <= Duration.ZERO) {
          updateState(
            ResetFingerprintsUiState.Finishing(
              state.completionToken,
              state.cancellationToken
            )
          )
        }
      }
    }

    val onCancel = {
      updateState(
        ResetFingerprintsUiState.Cancelling(
          cancellationToken = state.cancellationToken
        )
      )
    }

    return ScreenModel(
      body = AppDelayNotifyInProgressBodyModel(
        headline = "Fingerprint reset in progress...",
        delayInfoText = "You'll be able to add new fingerprints at the end of the 7-day security period.",
        cancelWarningText = "To continue using your current fingerprints, cancel the reset process.",
        cancelText = "Cancel reset",
        durationTitle = formattedDuration,
        progress = progress,
        remainingDelayPeriod = remainingDelayPeriod,
        onExit = props.onCancel,
        onStopRecovery = onCancel,
        eventTrackerScreenInfo = EventTrackerScreenInfo(
          eventTrackerScreenId = ResetFingerprintsEventTrackerScreenId.RESET_FINGERPRINTS_PROGRESS
        )
      ),
      presentationStyle = ScreenPresentationStyle.Modal
    )
  }

  @Composable
  private fun handleFinishing(
    state: ResetFingerprintsUiState.Finishing,
    props: ResetFingerprintsProps,
    updateState: (ResetFingerprintsUiState) -> Unit,
  ): ScreenModel {
    return ScreenModel(
      body = FinishResetFingerprintsBodyModel(
        onClose = props.onComplete,
        onConfirmReset = {
          // TODO: W-11569 finish fingerprint reset
          props.onComplete()
        },
        onCancelReset = {
          updateState(
            ResetFingerprintsUiState.Cancelling(
              cancellationToken = state.cancellationToken
            )
          )
        }
      ),
      presentationStyle = ScreenPresentationStyle.Modal
    )
  }

  @Composable
  private fun handleCancelling(
    state: ResetFingerprintsUiState.Cancelling,
    props: ResetFingerprintsProps,
    updateState: (ResetFingerprintsUiState) -> Unit,
  ): ScreenModel {
    LaunchedEffect("CancellingFingerprintReset") {
      fingerprintResetService.cancelFingerprintReset(
        cancellationToken = state.cancellationToken
      ).onSuccess {
        props.onCancel()
      }.onFailure { error ->
        logError { "Failed to cancel fingerprint reset: $error" }
        updateState(
          ResetFingerprintsUiState.Error.GenericError(
            title = "Cancellation Failed",
            message = "We couldn't cancel the fingerprint reset process. Please try again.",
            cause = PrivilegedActionThrowable(error),
            eventTrackerScreenId = ResetFingerprintsEventTrackerScreenId.ERROR_CANCELLING_RESET
          )
        )
      }
    }

    return LoadingBodyModel(
      id = ResetFingerprintsEventTrackerScreenId.CANCEL_FINGERPRINT_RESET_LOADING,
      message = "Cancelling fingerprint reset..."
    ).asModalScreen()
  }

  @Composable
  private fun handleError(
    state: ResetFingerprintsUiState.Error,
    props: ResetFingerprintsProps,
    updateState: (ResetFingerprintsUiState) -> Unit,
  ): ScreenModel {
    val errorTitle: String
    val errorMessage: String
    val onRetry: () -> Unit
    val errorData: ErrorData

    when (state) {
      is ResetFingerprintsUiState.Error.CreatePrivilegedActionError -> {
        errorTitle = "Error Starting Reset"
        errorMessage = "We couldn't start the fingerprint reset process. Error: ${state.error}. Please try again."
        onRetry = { updateState(ResetFingerprintsUiState.ShowingConfirmation()) }
        errorData = ErrorData(ResetFingerprintsSegment, "Starting fingerprint reset", PrivilegedActionThrowable(state.error))
      }
      is ResetFingerprintsUiState.Error.CompletePrivilegedActionError -> {
        errorTitle = "Error Completing Reset"
        errorMessage = "We couldn't finalize the fingerprint reset process. Error: ${state.error}. Please try again."
        onRetry = { updateState(ResetFingerprintsUiState.ShowingConfirmation()) }
        errorData = ErrorData(ResetFingerprintsSegment, "Completing fingerprint reset", PrivilegedActionThrowable(state.error))
      }
      is ResetFingerprintsUiState.Error.GenericError -> {
        errorTitle = state.title
        errorMessage = state.message
        onRetry = { updateState(ResetFingerprintsUiState.ShowingConfirmation()) }
        errorData = ErrorData(ResetFingerprintsSegment, "Generic error in flow", state.cause ?: RuntimeException(state.message))
      }
    }

    return ScreenModel(
      body = ErrorFormBodyModel(
        title = errorTitle,
        subline = errorMessage,
        primaryButton = ButtonDataModel(
          text = "Retry",
          onClick = onRetry
        ),
        secondaryButton = ButtonDataModel(
          text = "Cancel",
          onClick = props.onCancel
        ),
        errorData = errorData,
        eventTrackerScreenId = state.eventTrackerScreenId
      ),
      presentationStyle = ScreenPresentationStyle.Modal
    )
  }

  private sealed interface ResetFingerprintsUiState {
    /**
     * Showing the confirmation dialog before proceeding with the reset.
     */
    data class ShowingConfirmation(
      val bottomSheetModel: SheetModel? = null,
    ) : ResetFingerprintsUiState

    /**
     * Ready for NFC scan to create a grant request.
     */
    object ReadyForNfcScan : ResetFingerprintsUiState

    /**
     * Showing the progress of the fingerprint reset, indicating the delay & notify period.
     */
    data class ShowingProgress(
      val actionId: String,
      val startTime: Instant,
      val endTime: Instant,
      val completionToken: String,
      val cancellationToken: String,
    ) : ResetFingerprintsUiState

    /**
     * Cancelling the reset process.
     */
    data class Cancelling(
      val cancellationToken: String,
    ) : ResetFingerprintsUiState

    /**
     * Finishing the reset process, indicating that the user can now reset their fingerprints.
     */
    data class Finishing(
      val completionToken: String,
      val cancellationToken: String,
    ) : ResetFingerprintsUiState

    /**
     * Represents different error scenarios in the flow.
     */
    sealed interface Error : ResetFingerprintsUiState {
      val eventTrackerScreenId: EventTrackerScreenId

      data class CreatePrivilegedActionError(val error: PrivilegedActionError) : Error {
        override val eventTrackerScreenId: EventTrackerScreenId =
          ResetFingerprintsEventTrackerScreenId.ERROR_STARTING_RESET
      }

      data class CompletePrivilegedActionError(val error: PrivilegedActionError) : Error {
        override val eventTrackerScreenId: EventTrackerScreenId =
          ResetFingerprintsEventTrackerScreenId.ERROR_FINALIZING_RESET
      }

      data class GenericError(
        val title: String,
        val message: String,
        val cause: Throwable? = null,
        override val eventTrackerScreenId: EventTrackerScreenId,
      ) : Error
    }

    /**
     * Represents the initial loading state of the flow.
     */
    object Loading : ResetFingerprintsUiState
  }
}

private object ResetFingerprintsSegment : AppSegment {
  override val id: String = "ResetFingerprintsFlow"
}

private class PrivilegedActionThrowable(
  paError: PrivilegedActionError,
) : Throwable("PrivilegedActionError: $paError")
