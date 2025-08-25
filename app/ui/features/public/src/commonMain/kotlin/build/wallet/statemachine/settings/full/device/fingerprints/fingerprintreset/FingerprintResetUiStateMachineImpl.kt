package build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset

import androidx.compose.runtime.*
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.metrics.MetricOutcome
import bitkey.metrics.MetricTrackerService
import bitkey.privilegedactions.FingerprintResetService
import bitkey.privilegedactions.FingerprintResetState
import bitkey.privilegedactions.PrivilegedActionError
import build.wallet.Progress
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.EnrolledFingerprints.Companion.FIRST_FINGERPRINT_INDEX
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
import build.wallet.statemachine.root.RemainingRecoveryDelayWordsUpdateFrequency
import build.wallet.statemachine.settings.full.device.fingerprints.EnrollingFingerprintProps
import build.wallet.statemachine.settings.full.device.fingerprints.EnrollingFingerprintUiStateMachine
import build.wallet.statemachine.settings.full.device.fingerprints.EnrollmentContext
import build.wallet.statemachine.settings.full.device.fingerprints.FingerprintResetGrantNfcHandler
import build.wallet.statemachine.settings.full.device.fingerprints.metrics.FingerprintResetCompleteMetricDefinition
import build.wallet.statemachine.settings.full.device.fingerprints.metrics.FingerprintResetInitiateMetricDefinition
import build.wallet.time.DurationFormatter
import build.wallet.time.durationProgress
import build.wallet.time.nonNegativeDurationBetween
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

// TODO: W-11811 - break this class into smaller state machines
@Suppress("LargeClass")
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
  private val fingerprintResetGrantNfcHandler: FingerprintResetGrantNfcHandler,
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
        handleShowingDelayAndNotifyProgress(props, state) { newState -> uiState = newState }

      is FingerprintResetUiState.DelayAndNotifyComplete ->
        handleDelayAndNotifyComplete(state, props) { newState -> uiState = newState }

      is FingerprintResetUiState.RequestingSignedGrantFromServer ->
        handleRequestingSignedGrantFromServer(state) { newState -> uiState = newState }

      is FingerprintResetUiState.ProvidingGrantViaNfc ->
        handleProvidingGrantViaNfc(state) { newState -> uiState = newState }

      is FingerprintResetUiState.EnrollingFingerprints ->
        handleEnrollingFingerprints(state) { newState -> uiState = newState }

      is FingerprintResetUiState.Success ->
        handleSuccess(state, props)

      is FingerprintResetUiState.Cancelling ->
        handleCancelling(state, props) { newState -> uiState = newState }

      is FingerprintResetUiState.Error ->
        handleError(state) { newState -> uiState = newState }
    }
  }

  private fun handleInitialLoading(
    coroutineScope: CoroutineScope,
    updateState: (FingerprintResetUiState) -> Unit,
  ) {
    coroutineScope.launch {
      fingerprintResetService.getFingerprintResetState()
        .onSuccess { resetState ->
          when (resetState) {
            is FingerprintResetState.DelayCompleted -> {
              metricTrackerService.startMetric(
                metricDefinition = FingerprintResetCompleteMetricDefinition
              )
              when (val authStrategy = resetState.action.authorizationStrategy) {
                is AuthorizationStrategy.DelayAndNotify -> updateState(
                  FingerprintResetUiState.DelayAndNotifyComplete.DelayComplete(
                    actionId = resetState.action.id,
                    completionToken = authStrategy.completionToken,
                    cancellationToken = authStrategy.cancellationToken,
                    hasStartedCompleteMetric = true
                  )
                )
              }
            }
            is FingerprintResetState.DelayInProgress -> {
              updateState(
                FingerprintResetUiState.ShowingDelayAndNotifyProgress(
                  actionId = resetState.action.id,
                  startTime = resetState.delayAndNotify.delayStartTime,
                  endTime = resetState.delayAndNotify.delayEndTime,
                  completionToken = resetState.delayAndNotify.completionToken,
                  cancellationToken = resetState.delayAndNotify.cancellationToken
                )
              )
            }
            is FingerprintResetState.GrantReady -> {
              metricTrackerService.startMetric(
                metricDefinition = FingerprintResetCompleteMetricDefinition
              )
              updateState(
                FingerprintResetUiState.DelayAndNotifyComplete.ResumingFromLocalGrant(
                  grant = resetState.grant,
                  hasStartedCompleteMetric = true
                )
              )
            }
            is FingerprintResetState.None -> {
              updateState(FingerprintResetUiState.ShowingConfirmation())
            }
          }
        }
        .onFailure { error ->
          logError { "Failed to get fingerprint reset state: $error" }
          // Fallback to showing confirmation
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
            FingerprintResetGrantRequestResult.GrantRequestRetrieved(grantRequest)
          } else {
            FingerprintResetGrantRequestResult.FwUpRequired
          }
        },
        onSuccess = { result ->
          when (result) {
            is FingerprintResetGrantRequestResult.GrantRequestRetrieved -> {
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
            FingerprintResetGrantRequestResult.FwUpRequired -> {
              // Complete the initiate metric as canceled when firmware update is required
              completeResetInitiateMetric(MetricOutcome.UserCanceled)
              props.onFwUpRequired()
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
    props: FingerprintResetProps,
    state: FingerprintResetUiState.ShowingDelayAndNotifyProgress,
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
      while (true) {
        remainingDelayPeriod = nonNegativeDurationBetween(clock.now(), state.endTime)
        if (remainingDelayPeriod <= Duration.ZERO) {
          metricTrackerService.startMetric(
            metricDefinition = FingerprintResetCompleteMetricDefinition
          )
          updateState(
            FingerprintResetUiState.DelayAndNotifyComplete.DelayComplete(
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

    return FingerprintResetDelayAndNotifyProgressBodyModel(
      actionId = state.actionId,
      startTime = state.startTime,
      endTime = state.endTime,
      completionToken = state.completionToken,
      cancellationToken = state.cancellationToken
    ).toScreenModel(
      durationTitle = formattedDuration,
      progress = progress,
      remainingDelayPeriod = remainingDelayPeriod,
      onExit = props.onCancel,
      onStopRecovery = {
        updateState(
          FingerprintResetUiState.Cancelling(
            cancellationToken = state.cancellationToken
          )
        )
      }
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
            grant = grant
          )
        )
      }.onFailure { error ->
        logError { "Failed to complete fingerprint reset: $error" }
        completeResetCompleteMetric(MetricOutcome.Failed)
        updateState(
          FingerprintResetUiState.Error.CompletePrivilegedActionError(
            error = error,
            actionId = state.actionId,
            completionToken = state.completionToken,
            cancellationToken = state.cancellationToken
          )
        )
      }
    }

    return LoadingBodyModel(
      id = FingerprintResetEventTrackerScreenId.LOADING_GRANT,
      message = "Completing fingerprint reset..."
    ).asModalScreen()
  }

  @Composable
  private fun handleDelayAndNotifyComplete(
    state: FingerprintResetUiState.DelayAndNotifyComplete,
    props: FingerprintResetProps,
    updateState: (FingerprintResetUiState) -> Unit,
  ): ScreenModel {
    val coroutineScope = rememberCoroutineScope()
    return ScreenModel(
      body = FinishFingerprintResetBodyModel(
        onClose = {
          completeResetCompleteMetric(MetricOutcome.UserCanceled)
          props.onCancel()
        },
        onConfirmReset = {
          val onDismiss = {
            updateState(state)
          }
          val newBottomSheetModel = FingerprintResetConfirmationSheetModel(
            onDismiss = onDismiss,
            onConfirmReset = {
              coroutineScope.launch {
                handleGrantCompletionFlow(state, updateState)
              }
            }
          ).asSheetModalScreen(onClosed = onDismiss)

          updateState(
            when (state) {
              is FingerprintResetUiState.DelayAndNotifyComplete.DelayComplete ->
                state.copy(bottomSheetModel = newBottomSheetModel)
              is FingerprintResetUiState.DelayAndNotifyComplete.ResumingFromLocalGrant ->
                state.copy(bottomSheetModel = newBottomSheetModel)
            }
          )
        },
        onCancelReset = {
          when (state) {
            is FingerprintResetUiState.DelayAndNotifyComplete.DelayComplete -> {
              updateState(
                FingerprintResetUiState.Cancelling(
                  cancellationToken = state.cancellationToken,
                  isCompletePhase = true
                )
              )
            }
            is FingerprintResetUiState.DelayAndNotifyComplete.ResumingFromLocalGrant -> {
              // For resuming from local grant, we don't have cancellation tokens,
              // but the service can handle cancellation by deleting the local grant
              updateState(
                FingerprintResetUiState.Cancelling(
                  cancellationToken = "", // Empty token since we're cancelling a local grant
                  isCompletePhase = true
                )
              )
            }
          }
        }
      ),
      bottomSheetModel = state.bottomSheetModel,
      presentationStyle = ScreenPresentationStyle.Modal
    )
  }

  private suspend fun handleGrantCompletionFlow(
    state: FingerprintResetUiState.DelayAndNotifyComplete,
    updateState: (FingerprintResetUiState) -> Unit,
  ) {
    when (state) {
      is FingerprintResetUiState.DelayAndNotifyComplete.DelayComplete -> {
        // Always check for persisted grant first
        val persistedGrant = fingerprintResetService.getPendingFingerprintResetGrant().get()
        if (persistedGrant != null) {
          // Use persisted grant directly
          updateState(
            FingerprintResetUiState.ProvidingGrantViaNfc(
              grant = persistedGrant
            )
          )
        } else {
          // No persisted grant, request from server
          updateState(
            FingerprintResetUiState.RequestingSignedGrantFromServer(
              actionId = state.actionId,
              completionToken = state.completionToken,
              cancellationToken = state.cancellationToken
            )
          )
        }
      }
      is FingerprintResetUiState.DelayAndNotifyComplete.ResumingFromLocalGrant -> {
        // We already have the grant, go directly to providing it
        updateState(
          FingerprintResetUiState.ProvidingGrantViaNfc(
            grant = state.grant
          )
        )
      }
    }
  }

  @Composable
  private fun handleProvidingGrantViaNfc(
    state: FingerprintResetUiState.ProvidingGrantViaNfc,
    updateState: (FingerprintResetUiState) -> Unit,
  ): ScreenModel {
    return nfcSessionUIStateMachine.model(
      props = fingerprintResetGrantNfcHandler.createGrantProvisionProps(
        grant = state.grant,
        onSuccess = { result ->
          when (result) {
            is FingerprintResetGrantProvisionResult.ProvideGrantSuccess -> {
              updateState(FingerprintResetUiState.EnrollingFingerprints(grant = state.grant))
            }
            is FingerprintResetGrantProvisionResult.FingerprintResetComplete -> {
              completeResetCompleteMetric(MetricOutcome.Succeeded)
              updateState(FingerprintResetUiState.Success(result.enrolledFingerprints))
            }
            is FingerprintResetGrantProvisionResult.ProvideGrantFailed -> {
              val errorMessage = "Failed to provide grant during fingerprint reset."
              handleNfcProvideGrantError(errorMessage, state.grant, updateState)
            }
          }
        },
        onCancel = {
          handleNfcProvideGrantError(
            "NFC communication was cancelled during grant provision.",
            state.grant,
            updateState
          )
        },
        onError = { error ->
          // Handle all NFC errors during grant provision to preserve the grant for retry
          handleNfcProvideGrantError(
            "NFC error during grant provision: ${error.message ?: error::class.simpleName}",
            state.grant,
            updateState
          )
          true // Indicate we handled the error, don't show default error screen
        },
        eventTrackerContext = NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS_PROVIDE_SIGNED_GRANT
      )
    )
  }

  @Composable
  private fun handleEnrollingFingerprints(
    state: FingerprintResetUiState.EnrollingFingerprints,
    updateState: (FingerprintResetUiState) -> Unit,
  ): ScreenModel {
    return enrollingFingerprintUiStateMachine.model(
      EnrollingFingerprintProps(
        onCancel = {
          updateState(
            FingerprintResetUiState.DelayAndNotifyComplete.ResumingFromLocalGrant(
              grant = state.grant,
              hasStartedCompleteMetric = true
            )
          )
        },
        onSuccess = { enrolledFingerprints ->
          completeResetCompleteMetric(MetricOutcome.Succeeded)
          // Delete the grant immediately after successful enrollment
          fingerprintResetService.deleteFingerprintResetGrant()

          updateState(FingerprintResetUiState.Success(enrolledFingerprints))
        },
        fingerprintHandle = FingerprintHandle(
          index = FIRST_FINGERPRINT_INDEX,
          label = ""
        ),
        enrolledFingerprints = EnrolledFingerprints(
          fingerprintHandles = emptyList()
        ),
        context = EnrollmentContext.FingerprintReset(grant = state.grant)
      )
    )
  }

  @Composable
  private fun handleSuccess(
    state: FingerprintResetUiState.Success,
    props: FingerprintResetProps,
  ): ScreenModel {
    return ScreenModel(
      body = FingerprintResetSuccessBodyModel(
        onDone = {
          props.onComplete(state.enrolledFingerprints)
        }
      ),
      presentationStyle = ScreenPresentationStyle.Modal
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
          FingerprintResetUiState.Error.CancellingError(
            cause = PrivilegedActionThrowable(error)
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
      is FingerprintResetUiState.Error.CancellingError ->
        FingerprintResetErrorBodyModel.GenericError(
          title = "Cancellation Failed",
          message = "We couldn't cancel the fingerprint reset process. Please try again.",
          cause = state.cause,
          eventTrackerScreenId = state.eventTrackerScreenId
        )
    }

    // Determine the appropriate retry action based on the error type
    val onRetry = when {
      // For NFC errors where we have the grant, retry the grant provision step
      state is FingerprintResetUiState.Error.NfcError && state.grant != null -> {
        {
          updateState(
            FingerprintResetUiState.ProvidingGrantViaNfc(
              grant = state.grant
            )
          )
        }
      }
      // For server errors during grant completion phase, retry the server request
      state is FingerprintResetUiState.Error.CompletePrivilegedActionError -> {
        {
          updateState(
            FingerprintResetUiState.RequestingSignedGrantFromServer(
              actionId = state.actionId,
              completionToken = state.completionToken,
              cancellationToken = state.cancellationToken
            )
          )
        }
      }
      // For all other errors, go back to confirmation screen
      else -> {
        { updateState(FingerprintResetUiState.ShowingConfirmation()) }
      }
    }

    // Determine the appropriate cancel action based on the error type and context
    val onCancel = when {
      // For NFC errors where we have the grant, stay in flow and go back to grant delivery screen
      state is FingerprintResetUiState.Error.NfcError && state.grant != null -> {
        {
          updateState(
            FingerprintResetUiState.DelayAndNotifyComplete.ResumingFromLocalGrant(
              grant = state.grant,
              hasStartedCompleteMetric = true
            )
          )
        }
      }
      // For server errors during grant completion phase, return to finish screen
      state is FingerprintResetUiState.Error.CompletePrivilegedActionError -> {
        {
          updateState(
            FingerprintResetUiState.DelayAndNotifyComplete.DelayComplete(
              actionId = state.actionId,
              completionToken = state.completionToken,
              cancellationToken = state.cancellationToken,
              hasStartedCompleteMetric = true
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
        onCancel = onCancel
      ),
      presentationStyle = ScreenPresentationStyle.Modal
    )
  }

  private fun createNfcErrorBodyModel(
    nfcError: FingerprintResetUiState.Error.NfcError,
  ): FingerprintResetErrorBodyModel {
    val canRetryWithGrant = nfcError.grant != null

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
    grant: Grant,
    updateState: (FingerprintResetUiState) -> Unit,
  ) {
    completeResetCompleteMetric(MetricOutcome.Failed)
    updateState(
      FingerprintResetUiState.Error.NfcError(
        cause = RuntimeException(errorMessage),
        grant = grant
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
     * Represents the state when the delay and notify period is complete.
     */
    sealed interface DelayAndNotifyComplete : FingerprintResetUiState {
      val bottomSheetModel: SheetModel?
      val hasStartedCompleteMetric: Boolean

      /**
       * Delay period completed with action ID and completion/cancellation tokens.
       */
      data class DelayComplete(
        val actionId: String,
        val completionToken: String,
        val cancellationToken: String,
        override val bottomSheetModel: SheetModel? = null,
        override val hasStartedCompleteMetric: Boolean = false,
      ) : DelayAndNotifyComplete

      /**
       * Resuming from a previously persisted local grant.
       */
      data class ResumingFromLocalGrant(
        val grant: Grant,
        override val bottomSheetModel: SheetModel? = null,
        override val hasStartedCompleteMetric: Boolean = false,
      ) : DelayAndNotifyComplete
    }

    /**
     * Completing the fingerprint reset by fetching the grant and providing to the hardware.
     */
    data class RequestingSignedGrantFromServer(
      val actionId: String,
      val completionToken: String,
      val cancellationToken: String,
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

      data class CompletePrivilegedActionError(
        val error: PrivilegedActionError,
        val actionId: String,
        val completionToken: String,
        val cancellationToken: String,
      ) : Error {
        override val eventTrackerScreenId: EventTrackerScreenId =
          FingerprintResetEventTrackerScreenId.ERROR_FINALIZING_RESET
      }

      data class NfcError(
        val cause: Throwable? = null,
        val grant: Grant? = null,
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

      data class CancellingError(val cause: Throwable) : Error {
        override val eventTrackerScreenId: EventTrackerScreenId =
          FingerprintResetEventTrackerScreenId.ERROR_CANCELLING_RESET
      }
    }

    /**
     * Represents the initial loading state of the flow.
     */
    object Loading : FingerprintResetUiState

    /**
     * Represents the state when the fingerprint enrollment is in progress.
     */
    data class EnrollingFingerprints(
      val grant: Grant,
    ) : FingerprintResetUiState

    /**
     * Represents the state when the signed grant is being provided via NFC.
     */
    data class ProvidingGrantViaNfc(
      val grant: Grant,
    ) : FingerprintResetUiState

    /**
     * Represents the success state after fingerprint enrollment is complete.
     */
    data class Success(
      val enrolledFingerprints: EnrolledFingerprints,
    ) : FingerprintResetUiState
  }
}

internal object FingerprintResetSegment : AppSegment {
  override val id: String = "FingerprintResetFlow"
}
