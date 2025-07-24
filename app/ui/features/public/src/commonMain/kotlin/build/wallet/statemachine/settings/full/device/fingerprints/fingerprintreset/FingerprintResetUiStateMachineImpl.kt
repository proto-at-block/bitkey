package build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset

import androidx.compose.runtime.*
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.firmware.HardwareUnlockInfoService
import bitkey.metrics.MetricOutcome
import bitkey.metrics.MetricTrackerService
import bitkey.privilegedactions.FingerprintResetService
import bitkey.privilegedactions.PrivilegedActionError
import bitkey.privilegedactions.isDelayAndNotifyReadyToComplete
import build.wallet.Progress
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintHandle
import build.wallet.firmware.FirmwareFeatureFlag
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.logging.logError
import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.statemachine.root.RemainingRecoveryDelayWordsUpdateFrequency
import build.wallet.statemachine.settings.full.device.fingerprints.EnrollingFingerprintProps
import build.wallet.statemachine.settings.full.device.fingerprints.EnrollingFingerprintUiStateMachine
import build.wallet.statemachine.settings.full.device.fingerprints.EnrollmentContext
import build.wallet.statemachine.settings.full.device.fingerprints.metrics.FingerprintResetCompleteMetricDefinition
import build.wallet.statemachine.settings.full.device.fingerprints.metrics.FingerprintResetInitiateMetricDefinition
import build.wallet.time.DurationFormatter
import build.wallet.time.durationProgress
import build.wallet.time.nonNegativeDurationBetween
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

@BitkeyInject(ActivityScope::class)
class FingerprintResetUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val clock: Clock,
  private val durationFormatter: DurationFormatter,
  private val fingerprintResetService: FingerprintResetService,
  private val remainingRecoveryDelayWordsUpdateFrequency:
    RemainingRecoveryDelayWordsUpdateFrequency,
  private val enrollingFingerprintUiStateMachine: EnrollingFingerprintUiStateMachine,
  private val metricTrackerService: MetricTrackerService,
  private val hardwareUnlockInfoService: HardwareUnlockInfoService,
) : FingerprintResetUiStateMachine {
  @Composable
  override fun model(props: FingerprintResetProps): ScreenModel {
    var uiState: FingerprintResetUiState by remember {
      mutableStateOf(FingerprintResetUiState.Loading)
    }

    val coroutineScope = rememberCoroutineScope()

    return when (val state = uiState) {
      is FingerprintResetUiState.Loading -> {
        LaunchedEffect("loading-fingerprint-reset-status") {
          handleInitialLoading(coroutineScope) { newState -> uiState = newState }
        }
        ScreenModel(
          body = LoadingBodyModel(
            id = FingerprintResetEventTrackerScreenId.LOADING_FINGERPRINT_RESET_STATUS,
            onBack = props.onCancel
          ),
          presentationStyle = ScreenPresentationStyle.Modal
        )
      }

      is FingerprintResetUiState.ShowingConfirmation ->
        handleShowingConfirmation(state, props) { newState -> uiState = newState }

      is FingerprintResetUiState.CreatingGrantRequestViaNfc ->
        handleCreatingGrantRequestViaNfc(props, coroutineScope) { newState -> uiState = newState }

      is FingerprintResetUiState.ShowingDelayAndNotifyProgress ->
        handleShowingDelayAndNotifyProgress(state, props) { newState -> uiState = newState }

      is FingerprintResetUiState.DelayAndNotifyComplete ->
        handleDelayAndNotifyComplete(state, props) { newState -> uiState = newState }

      is FingerprintResetUiState.RequestingSignedGrantFromServer ->
        handleRequestingSignedGrantFromServer(state) { newState -> uiState = newState }

      is FingerprintResetUiState.ProvidingGrantViaNfc ->
        handleProvidingGrantViaNfc(state) { newState -> uiState = newState }

      is FingerprintResetUiState.EnrollingFingerprints ->
        handleEnrollingFingerprints(props)

      is FingerprintResetUiState.Cancelling ->
        handleCancelling(state, props) { newState -> uiState = newState }

      is FingerprintResetUiState.Error ->
        handleError(state, props) { newState -> uiState = newState }
    }
  }

  private fun handleInitialLoading(
    coroutineScope: CoroutineScope,
    updateState: (FingerprintResetUiState) -> Unit,
  ) {
    coroutineScope.launch {
      fingerprintResetService.getLatestFingerprintResetAction()
        .onSuccess { pendingAction: PrivilegedActionInstance? ->
          when (val authStrategy = pendingAction?.authorizationStrategy) {
            is AuthorizationStrategy.DelayAndNotify -> {
              updateState(
                if (pendingAction.isDelayAndNotifyReadyToComplete(clock)) {
                  metricTrackerService.startMetric(
                    metricDefinition = FingerprintResetCompleteMetricDefinition
                  )
                  FingerprintResetUiState.DelayAndNotifyComplete(
                    actionId = pendingAction.id,
                    completionToken = authStrategy.completionToken,
                    cancellationToken = authStrategy.cancellationToken,
                    hasStartedCompleteMetric = true
                  )
                } else {
                  FingerprintResetUiState.ShowingDelayAndNotifyProgress(
                    actionId = pendingAction.id,
                    startTime = authStrategy.delayStartTime,
                    endTime = authStrategy.delayEndTime,
                    completionToken = authStrategy.completionToken,
                    cancellationToken = authStrategy.cancellationToken
                  )
                }
              )
            }
            else -> {
              updateState(FingerprintResetUiState.ShowingConfirmation())
            }
          }
        }
        .onFailure { error ->
          logError { "Failed to get latest fingerprint reset action: $error" }
          updateState(FingerprintResetUiState.ShowingConfirmation())
        }
    }
  }

  @Composable
  private fun handleShowingConfirmation(
    state: FingerprintResetUiState.ShowingConfirmation,
    props: FingerprintResetProps,
    updateState: (FingerprintResetUiState) -> Unit,
  ): ScreenModel {
    return ScreenModel(
      body = FingerprintResetConfirmationBodyModel(
        onClose = props.onCancel,
        onConfirmReset = {
          val onDismiss = {
            updateState(FingerprintResetUiState.ShowingConfirmation())
          }
          updateState(
            state.copy(
              bottomSheetModel = FingerprintResetConfirmationSheetModel(
                onDismiss = onDismiss,
                onConfirmReset = {
                  metricTrackerService.startMetric(
                    metricDefinition = FingerprintResetInitiateMetricDefinition
                  )
                  updateState(FingerprintResetUiState.CreatingGrantRequestViaNfc)
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
  private fun handleCreatingGrantRequestViaNfc(
    props: FingerprintResetProps,
    coroutineScope: CoroutineScope,
    updateState: (FingerprintResetUiState) -> Unit,
  ): ScreenModel {
    return nfcSessionUIStateMachine.model(
      props = NfcSessionUIStateMachineProps(
        session = { session, commands ->
          // Check that the firmware supports fingerprint reset
          val enabled = commands.getFirmwareFeatureFlags(session)
            .find { it.flag == FirmwareFeatureFlag.FINGERPRINT_RESET }
            ?.enabled ?: false

          if (enabled) {
            val grantRequest = commands.getGrantRequest(session, GrantAction.FINGERPRINT_RESET)
            FingerprintResetNfcResult.GrantRequestRetrieved(grantRequest)
          } else {
            FingerprintResetNfcResult.FwUpRequired
          }
        },
        onSuccess = { result ->
          when (result) {
            is FingerprintResetNfcResult.GrantRequestRetrieved -> {
              coroutineScope.launch {
                val serviceResult =
                  fingerprintResetService.createFingerprintResetPrivilegedAction(
                    grantRequest = result.grantRequest
                  )

                serviceResult
                  .onSuccess { actionInstance ->
                    when (val authStrategy = actionInstance.authorizationStrategy) {
                      is AuthorizationStrategy.DelayAndNotify -> {
                        completeResetInitiateMetric(MetricOutcome.Succeeded)
                        updateState(
                          FingerprintResetUiState.ShowingDelayAndNotifyProgress(
                            actionId = actionInstance.id,
                            startTime = authStrategy.delayStartTime,
                            endTime = authStrategy.delayEndTime,
                            completionToken = authStrategy.completionToken,
                            cancellationToken = authStrategy.cancellationToken
                          )
                        )
                      }
                    }
                  }
                  .onFailure { error ->
                    logError { "Failed to create fingerprint reset privileged action: $error" }
                    completeResetInitiateMetric(MetricOutcome.Failed)
                    updateState(FingerprintResetUiState.Error.CreatePrivilegedActionError(error))
                  }
              }
            }
            FingerprintResetNfcResult.FwUpRequired -> {
              // Complete the initiate metric as canceled when firmware update is required
              completeResetInitiateMetric(MetricOutcome.UserCanceled)
              props.onFwUpRequired()
            }
            else -> {
              completeResetInitiateMetric(MetricOutcome.Failed)
              updateState(
                FingerprintResetUiState.Error.GenericError(
                  title = "NFC Error",
                  message = "There was an issue communicating with your hardware. Please try again.",
                  cause = RuntimeException("NFC operation returned unexpected result: $result after grant request retrieval."),
                  eventTrackerScreenId = FingerprintResetEventTrackerScreenId.ERROR_NFC_OPERATION_FAILED
                )
              )
            }
          }
        },
        onCancel = {
          completeResetInitiateMetric(MetricOutcome.UserCanceled)
          updateState(FingerprintResetUiState.ShowingConfirmation())
        },
        screenPresentationStyle = ScreenPresentationStyle.Modal,
        eventTrackerContext = NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS_CREATE_GRANT_REQUEST,
        needsAuthentication = false,
        hardwareVerification = NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
      )
    )
  }

  @Composable
  private fun handleShowingDelayAndNotifyProgress(
    state: FingerprintResetUiState.ShowingDelayAndNotifyProgress,
    props: FingerprintResetProps,
    updateState: (FingerprintResetUiState) -> Unit,
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

    LaunchedEffect("update-reset-progress", state.endTime) {
      // Update countdown display every minute for UX, but transition
      // immediately when delay period completes
      while (true) {
        remainingDelayPeriod = nonNegativeDurationBetween(clock.now(), state.endTime)
        if (remainingDelayPeriod <= Duration.ZERO) {
          metricTrackerService.startMetric(
            metricDefinition = FingerprintResetCompleteMetricDefinition
          )
          updateState(
            FingerprintResetUiState.DelayAndNotifyComplete(
              actionId = state.actionId,
              completionToken = state.completionToken,
              cancellationToken = state.cancellationToken,
              hasStartedCompleteMetric = true
            )
          )
          break
        }

        val nextUpdateDelay = minOf(
          remainingDelayPeriod,
          remainingRecoveryDelayWordsUpdateFrequency.value
        )

        delay(nextUpdateDelay)
      }
    }

    val onCancel = {
      updateState(
        FingerprintResetUiState.Cancelling(
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
          eventTrackerScreenId = FingerprintResetEventTrackerScreenId.RESET_FINGERPRINTS_PROGRESS
        )
      ),
      presentationStyle = ScreenPresentationStyle.Modal
    )
  }

  @Composable
  private fun handleDelayAndNotifyComplete(
    state: FingerprintResetUiState.DelayAndNotifyComplete,
    props: FingerprintResetProps,
    updateState: (FingerprintResetUiState) -> Unit,
  ): ScreenModel {
    return ScreenModel(
      body = FinishFingerprintResetBodyModel(
        onClose = {
          completeResetCompleteMetric(MetricOutcome.UserCanceled)
          props.onCancel()
        },
        onConfirmReset = {
          val onDismiss = {
            updateState(
              FingerprintResetUiState.DelayAndNotifyComplete(
                actionId = state.actionId,
                completionToken = state.completionToken,
                cancellationToken = state.cancellationToken,
                hasStartedCompleteMetric = state.hasStartedCompleteMetric
              )
            )
          }
          updateState(
            state.copy(
              bottomSheetModel = FingerprintResetConfirmationSheetModel(
                onDismiss = onDismiss,
                onConfirmReset = {
                  // Don't start metric again since we started it when entering this state
                  updateState(
                    FingerprintResetUiState.RequestingSignedGrantFromServer(
                      actionId = state.actionId,
                      completionToken = state.completionToken
                    )
                  )
                }
              ).asSheetModalScreen(onClosed = onDismiss)
            )
          )
        },
        onCancelReset = {
          updateState(
            FingerprintResetUiState.Cancelling(
              cancellationToken = state.cancellationToken,
              isCompletePhase = true
            )
          )
        }
      ),
      bottomSheetModel = state.bottomSheetModel,
      presentationStyle = ScreenPresentationStyle.Modal
    )
  }

  @Composable
  private fun handleRequestingSignedGrantFromServer(
    state: FingerprintResetUiState.RequestingSignedGrantFromServer,
    updateState: (FingerprintResetUiState) -> Unit,
  ): ScreenModel {
    LaunchedEffect("complete-fingerprint-reset") {
      fingerprintResetService.completeFingerprintResetAndGetGrant(
        actionId = state.actionId,
        completionToken = state.completionToken
      ).onSuccess { grant ->
        updateState(
          FingerprintResetUiState.ProvidingGrantViaNfc(
            actionId = state.actionId,
            grant = grant,
            completionToken = state.completionToken
          )
        )
      }.onFailure { error ->
        logError { "Failed to complete fingerprint reset: $error" }
        completeResetCompleteMetric(MetricOutcome.Failed)
        updateState(
          FingerprintResetUiState.Error.CompletePrivilegedActionError(error)
        )
      }
    }

    return LoadingBodyModel(
      id = FingerprintResetEventTrackerScreenId.LOADING_GRANT,
      message = "Completing fingerprint reset..."
    ).asModalScreen()
  }

  @Composable
  private fun handleProvidingGrantViaNfc(
    state: FingerprintResetUiState.ProvidingGrantViaNfc,
    updateState: (FingerprintResetUiState) -> Unit,
  ): ScreenModel {
    return nfcSessionUIStateMachine.model(
      props = NfcSessionUIStateMachineProps(
        session = { session, commands ->
          if (commands.provideGrant(session, state.grant)) {
            commands.startFingerprintEnrollment(session)
            FingerprintResetNfcResult.ProvideGrantSuccess
          } else {
            FingerprintResetNfcResult.ProvideGrantFailed
          }
        },
        onSuccess = { result ->
          when (result) {
            is FingerprintResetNfcResult.ProvideGrantSuccess -> {
              // Clear fingerprints immediately after successfully providing the grant
              hardwareUnlockInfoService.clear()
              updateState(FingerprintResetUiState.EnrollingFingerprints)
            }
            is FingerprintResetNfcResult.ProvideGrantFailed ->
              handleNfcProvideGrantError(
                "Failed to provide grant during fingerprint reset.",
                state.actionId,
                state.grant,
                state.completionToken,
                updateState
              )
            else ->
              handleNfcProvideGrantError(
                "NFC operation returned unexpected result: $result",
                state.actionId,
                state.grant,
                state.completionToken,
                updateState
              )
          }
        },
        onCancel = {
          handleNfcProvideGrantError(
            "NFC communication was cancelled during grant provision.",
            state.actionId,
            state.grant,
            state.completionToken,
            updateState
          )
        },
        onError = { error ->
          // Handle all NFC errors during grant provision to preserve the grant for retry
          handleNfcProvideGrantError(
            "NFC error during grant provision: ${error.message ?: error::class.simpleName}",
            state.actionId,
            state.grant,
            state.completionToken,
            updateState
          )
          true // Indicate we handled the error, don't show default error screen
        },
        screenPresentationStyle = ScreenPresentationStyle.Modal,
        eventTrackerContext = NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS_PROVIDE_SIGNED_GRANT,
        needsAuthentication = false,
        shouldLock = false,
        hardwareVerification = NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
      )
    )
  }

  @Composable
  private fun handleEnrollingFingerprints(props: FingerprintResetProps): ScreenModel {
    return enrollingFingerprintUiStateMachine.model(
      EnrollingFingerprintProps(
        onCancel = props.onCancel,
        onSuccess = { enrolledFingerprints ->
          completeResetCompleteMetric(MetricOutcome.Succeeded)
          props.onComplete(enrolledFingerprints)
        },
        fingerprintHandle = FingerprintHandle(
          index = 0,
          label = ""
        ),
        enrolledFingerprints = EnrolledFingerprints(
          fingerprintHandles = emptyList()
        ),
        context = EnrollmentContext.FingerprintReset
      )
    )
  }

  @Composable
  private fun handleCancelling(
    state: FingerprintResetUiState.Cancelling,
    props: FingerprintResetProps,
    updateState: (FingerprintResetUiState) -> Unit,
  ): ScreenModel {
    LaunchedEffect("CancellingFingerprintReset") {
      fingerprintResetService.cancelFingerprintReset(
        cancellationToken = state.cancellationToken
      ).onSuccess {
        if (state.isCompletePhase) {
          completeResetCompleteMetric(MetricOutcome.UserCanceled)
        } else {
          completeResetInitiateMetric(MetricOutcome.UserCanceled)
        }
        props.onCancel()
      }.onFailure { error ->
        logError { "Failed to cancel fingerprint reset: $error" }
        updateState(
          FingerprintResetUiState.Error.GenericError(
            title = "Cancellation Failed",
            message = "We couldn't cancel the fingerprint reset process. Please try again.",
            cause = PrivilegedActionThrowable(error),
            eventTrackerScreenId = FingerprintResetEventTrackerScreenId.ERROR_CANCELLING_RESET
          )
        )
      }
    }

    return LoadingBodyModel(
      id = FingerprintResetEventTrackerScreenId.CANCEL_FINGERPRINT_RESET_LOADING,
      message = "Cancelling fingerprint reset..."
    ).asModalScreen()
  }

  @Composable
  private fun handleError(
    state: FingerprintResetUiState.Error,
    props: FingerprintResetProps,
    updateState: (FingerprintResetUiState) -> Unit,
  ): ScreenModel {
    val errorBodyModel = when (state) {
      is FingerprintResetUiState.Error.CreatePrivilegedActionError ->
        FingerprintResetErrorBodyModel.CreatePrivilegedActionError(state.error)
      is FingerprintResetUiState.Error.CompletePrivilegedActionError ->
        FingerprintResetErrorBodyModel.CompletePrivilegedActionError(state.error)
      is FingerprintResetUiState.Error.NfcError -> {
        createNfcErrorBodyModel(state)
      }
      is FingerprintResetUiState.Error.GenericError ->
        FingerprintResetErrorBodyModel.GenericError(
          title = state.title,
          message = state.message,
          cause = state.cause,
          eventTrackerScreenId = state.eventTrackerScreenId
        )
    }

    // Determine the appropriate retry action based on the error type
    val onRetry = when {
      // For NFC errors where we have the grant, retry the grant provision step
      state is FingerprintResetUiState.Error.NfcError &&
        state.actionId != null && state.grant != null -> {
        {
          updateState(
            FingerprintResetUiState.ProvidingGrantViaNfc(
              actionId = state.actionId,
              grant = state.grant,
              completionToken = state.completionToken
            )
          )
        }
      }
      // For all other errors, go back to confirmation screen
      else -> {
        { updateState(FingerprintResetUiState.ShowingConfirmation()) }
      }
    }

    return ScreenModel(
      body = errorBodyModel.toFormBodyModel(
        onRetry = onRetry,
        onCancel = props.onCancel
      ),
      presentationStyle = ScreenPresentationStyle.Modal
    )
  }

  private fun createNfcErrorBodyModel(
    nfcError: FingerprintResetUiState.Error.NfcError,
  ): FingerprintResetErrorBodyModel {
    val canRetryWithGrant = nfcError.actionId != null && nfcError.grant != null

    return if (canRetryWithGrant) {
      FingerprintResetErrorBodyModel.NfcError(
        cause = nfcError.cause
      )
    } else {
      // If we don't have the grant anymore, show a more specific error
      FingerprintResetErrorBodyModel.GenericError(
        title = "NFC Communication Error",
        message = "There was an issue communicating with your hardware. Unfortunately, we cannot retry this specific operation. You'll need to restart the process.",
        cause = nfcError.cause,
        eventTrackerScreenId = FingerprintResetEventTrackerScreenId.ERROR_NFC_OPERATION_FAILED
      )
    }
  }

  private fun completeResetInitiateMetric(outcome: MetricOutcome) {
    metricTrackerService.completeMetric(
      metricDefinition = FingerprintResetInitiateMetricDefinition,
      outcome = outcome
    )
  }

  private fun completeResetCompleteMetric(outcome: MetricOutcome) {
    metricTrackerService.completeMetric(
      metricDefinition = FingerprintResetCompleteMetricDefinition,
      outcome = outcome
    )
  }

  private fun handleNfcProvideGrantError(
    errorMessage: String,
    actionId: String,
    grant: Grant,
    completionToken: String? = null,
    updateState: (FingerprintResetUiState) -> Unit,
  ) {
    completeResetCompleteMetric(MetricOutcome.Failed)
    updateState(
      FingerprintResetUiState.Error.NfcError(
        cause = RuntimeException(errorMessage),
        actionId = actionId,
        grant = grant,
        completionToken = completionToken
      )
    )
  }

  private sealed interface FingerprintResetUiState {
    /**
     * Showing the confirmation dialog before proceeding with the reset.
     */
    data class ShowingConfirmation(
      val bottomSheetModel: SheetModel? = null,
    ) : FingerprintResetUiState

    /**
     * Ready for NFC scan to create a grant request.
     */
    object CreatingGrantRequestViaNfc : FingerprintResetUiState

    /**
     * Showing the progress of the fingerprint reset, indicating the delay & notify period.
     */
    data class ShowingDelayAndNotifyProgress(
      val actionId: String,
      val startTime: Instant,
      val endTime: Instant,
      val completionToken: String,
      val cancellationToken: String,
    ) : FingerprintResetUiState

    /**
     * Cancelling the reset process.
     */
    data class Cancelling(
      val cancellationToken: String,
      val isCompletePhase: Boolean = false,
    ) : FingerprintResetUiState

    /**
     * Represents the state when the delay and notify period is complete,
     * and the grant is ready to be retrieved.
     */
    data class DelayAndNotifyComplete(
      val actionId: String,
      val completionToken: String,
      val cancellationToken: String,
      val bottomSheetModel: SheetModel? = null,
      val hasStartedCompleteMetric: Boolean = false,
    ) : FingerprintResetUiState

    /**
     * Completing the fingerprint reset by fetching the grant and providing to the hardware.
     */
    data class RequestingSignedGrantFromServer(
      val actionId: String,
      val completionToken: String,
    ) : FingerprintResetUiState

    /**
     * Represents different error scenarios in the flow.
     */
    sealed interface Error : FingerprintResetUiState {
      val eventTrackerScreenId: EventTrackerScreenId

      data class CreatePrivilegedActionError(val error: PrivilegedActionError) : Error {
        override val eventTrackerScreenId: EventTrackerScreenId =
          FingerprintResetEventTrackerScreenId.ERROR_STARTING_RESET
      }

      data class CompletePrivilegedActionError(val error: PrivilegedActionError) : Error {
        override val eventTrackerScreenId: EventTrackerScreenId =
          FingerprintResetEventTrackerScreenId.ERROR_FINALIZING_RESET
      }

      data class NfcError(
        val cause: Throwable? = null,
        val actionId: String? = null,
        val grant: Grant? = null,
        val completionToken: String? = null,
      ) : Error {
        override val eventTrackerScreenId: EventTrackerScreenId =
          FingerprintResetEventTrackerScreenId.ERROR_NFC_OPERATION_FAILED
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
    object Loading : FingerprintResetUiState

    /**
     * Represents the state when the fingerprint enrollment is in progress.
     */
    object EnrollingFingerprints : FingerprintResetUiState

    /**
     * Represents the state when the signed grant is being provided via NFC.
     */
    data class ProvidingGrantViaNfc(
      val actionId: String,
      val grant: Grant,
      val completionToken: String? = null,
    ) : FingerprintResetUiState
  }
}

internal object FingerprintResetSegment : AppSegment {
  override val id: String = "FingerprintResetFlow"
}
