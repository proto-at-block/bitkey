package build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints

import androidx.compose.runtime.*
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.f8e.privilegedactions.isDelayAndNotifyReadyToComplete
import bitkey.metrics.MetricOutcome
import bitkey.metrics.MetricTrackerService
import bitkey.privilegedactions.FingerprintResetService
import bitkey.privilegedactions.PrivilegedActionError
import build.wallet.Progress
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FirmwareFeatureFlag
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.logging.logError
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareProps
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachine
import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.statemachine.root.RemainingRecoveryDelayWordsUpdateFrequency
import build.wallet.statemachine.settings.full.device.fingerprints.metrics.FingerprintResetMetricDefinition
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
class ResetFingerprintsUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val clock: Clock,
  private val durationFormatter: DurationFormatter,
  private val fingerprintResetService: FingerprintResetService,
  private val remainingRecoveryDelayWordsUpdateFrequency:
    RemainingRecoveryDelayWordsUpdateFrequency,
  private val pairNewHardwareUiStateMachine: PairNewHardwareUiStateMachine,
  private val metricTrackerService: MetricTrackerService,
) : ResetFingerprintsUiStateMachine {
  @Composable
  override fun model(props: ResetFingerprintsProps): ScreenModel {
    var uiState: ResetFingerprintsUiState by remember {
      mutableStateOf(ResetFingerprintsUiState.Loading)
    }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState == ResetFingerprintsUiState.Loading) {
      if (uiState == ResetFingerprintsUiState.Loading) {
        handleInitialLoading(coroutineScope) { newState -> uiState = newState }
      }
    }

    return when (val state = uiState) {
      is ResetFingerprintsUiState.Loading -> {
        ScreenModel(
          body = LoadingBodyModel(
            id = ResetFingerprintsEventTrackerScreenId.LOADING_FINGERPRINT_RESET_STATUS,
            onBack = props.onCancel
          ),
          presentationStyle = ScreenPresentationStyle.Modal
        )
      }

      is ResetFingerprintsUiState.ShowingConfirmation ->
        handleShowingConfirmation(state, props) { newState -> uiState = newState }

      is ResetFingerprintsUiState.CreatingGrantRequestViaNfc ->
        handleCreatingGrantRequestViaNfc(props, coroutineScope) { newState -> uiState = newState }

      is ResetFingerprintsUiState.ShowingDelayAndNotifyProgress ->
        handleShowingDelayAndNotifyProgress(state, props) { newState -> uiState = newState }

      is ResetFingerprintsUiState.DelayAndNotifyComplete ->
        handleDelayAndNotifyComplete(state, props) { newState -> uiState = newState }

      is ResetFingerprintsUiState.RequestingSignedGrantFromServer ->
        handleRequestingSignedGrantFromServer(state) { newState -> uiState = newState }

      is ResetFingerprintsUiState.ProvidingGrantViaNfc ->
        handleProvidingGrantViaNfc(state, props) { newState -> uiState = newState }

      is ResetFingerprintsUiState.EnrollingFingerprints ->
        handleEnrollingFingerprints(props)

      is ResetFingerprintsUiState.Cancelling ->
        handleCancelling(state, props) { newState -> uiState = newState }

      is ResetFingerprintsUiState.Error ->
        handleError(state, props) { newState -> uiState = newState }
    }
  }

  private fun handleInitialLoading(
    coroutineScope: CoroutineScope,
    updateState: (ResetFingerprintsUiState) -> Unit,
  ) {
    coroutineScope.launch {
      fingerprintResetService.getLatestFingerprintResetAction()
        .onSuccess { pendingAction: PrivilegedActionInstance? ->
          when (val authStrategy = pendingAction?.authorizationStrategy) {
            is AuthorizationStrategy.DelayAndNotify -> {
              updateState(
                if (pendingAction.isDelayAndNotifyReadyToComplete(clock)) {
                  ResetFingerprintsUiState.DelayAndNotifyComplete(
                    actionId = pendingAction.id,
                    completionToken = authStrategy.completionToken,
                    cancellationToken = authStrategy.cancellationToken
                  )
                } else {
                  ResetFingerprintsUiState.ShowingDelayAndNotifyProgress(
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
              updateState(ResetFingerprintsUiState.ShowingConfirmation())
            }
          }
        }
        .onFailure { error ->
          logError { "Failed to get latest fingerprint reset action: $error" }
          updateState(ResetFingerprintsUiState.ShowingConfirmation())
        }
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
                  metricTrackerService.startMetric(
                    metricDefinition = FingerprintResetMetricDefinition
                  )
                  updateState(ResetFingerprintsUiState.CreatingGrantRequestViaNfc)
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
    props: ResetFingerprintsProps,
    coroutineScope: CoroutineScope,
    updateState: (ResetFingerprintsUiState) -> Unit,
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
            ResetFingerprintsNfcResult.GrantRequestRetrieved(grantRequest)
          } else {
            ResetFingerprintsNfcResult.FwUpRequired
          }
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
                    when (val authStrategy = actionInstance.authorizationStrategy) {
                      is AuthorizationStrategy.DelayAndNotify -> {
                        updateState(
                          ResetFingerprintsUiState.ShowingDelayAndNotifyProgress(
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
                    completeResetMetric(MetricOutcome.Failed)
                    updateState(ResetFingerprintsUiState.Error.CreatePrivilegedActionError(error))
                  }
              }
            }
            ResetFingerprintsNfcResult.FwUpRequired -> {
              completeResetMetric(MetricOutcome.FwUpdateRequired)
              props.onFwUpRequired()
            }
            else -> {
              completeResetMetric(MetricOutcome.Failed)
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
          completeResetMetric(MetricOutcome.UserCanceled)
          updateState(ResetFingerprintsUiState.ShowingConfirmation())
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
    state: ResetFingerprintsUiState.ShowingDelayAndNotifyProgress,
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

    LaunchedEffect("update-reset-progress", state.endTime) {
      // Update countdown display every minute for UX, but transition
      // immediately when delay period completes
      while (true) {
        remainingDelayPeriod = nonNegativeDurationBetween(clock.now(), state.endTime)
        if (remainingDelayPeriod <= Duration.ZERO) {
          updateState(
            ResetFingerprintsUiState.DelayAndNotifyComplete(
              actionId = state.actionId,
              completionToken = state.completionToken,
              cancellationToken = state.cancellationToken
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
  private fun handleDelayAndNotifyComplete(
    state: ResetFingerprintsUiState.DelayAndNotifyComplete,
    props: ResetFingerprintsProps,
    updateState: (ResetFingerprintsUiState) -> Unit,
  ): ScreenModel {
    return ScreenModel(
      body = FinishResetFingerprintsBodyModel(
        onClose = props.onCancel,
        onConfirmReset = {
          val onDismiss = {
            updateState(
              ResetFingerprintsUiState.DelayAndNotifyComplete(
                actionId = state.actionId,
                completionToken = state.completionToken,
                cancellationToken = state.cancellationToken
              )
            )
          }
          updateState(
            state.copy(
              bottomSheetModel = ResetFingerprintsConfirmationSheetModel(
                onDismiss = onDismiss,
                onConfirmReset = {
                  updateState(
                    ResetFingerprintsUiState.RequestingSignedGrantFromServer(
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
            ResetFingerprintsUiState.Cancelling(
              cancellationToken = state.cancellationToken
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
    state: ResetFingerprintsUiState.RequestingSignedGrantFromServer,
    updateState: (ResetFingerprintsUiState) -> Unit,
  ): ScreenModel {
    LaunchedEffect("complete-fingerprint-reset") {
      fingerprintResetService.completeFingerprintResetAndGetGrant(
        actionId = state.actionId,
        completionToken = state.completionToken
      ).onSuccess { grant ->
        updateState(
          ResetFingerprintsUiState.ProvidingGrantViaNfc(
            actionId = state.actionId,
            grant = grant
          )
        )
      }.onFailure { error ->
        logError { "Failed to complete fingerprint reset: $error" }
        completeResetMetric(MetricOutcome.Failed)
        updateState(
          ResetFingerprintsUiState.Error.CompletePrivilegedActionError(error)
        )
      }
    }

    return LoadingBodyModel(
      id = ResetFingerprintsEventTrackerScreenId.LOADING_GRANT,
      message = "Completing fingerprint reset..."
    ).asModalScreen()
  }

  @Composable
  private fun handleProvidingGrantViaNfc(
    state: ResetFingerprintsUiState.ProvidingGrantViaNfc,
    props: ResetFingerprintsProps,
    updateState: (ResetFingerprintsUiState) -> Unit,
  ): ScreenModel {
    return nfcSessionUIStateMachine.model(
      props = NfcSessionUIStateMachineProps(
        session = { session, commands ->
          commands.provideGrant(session, state.grant)
          commands.startFingerprintEnrollment(session)
          ResetFingerprintsNfcResult.ProvideGrantSuccess
        },
        onSuccess = { result ->
          updateState(ResetFingerprintsUiState.EnrollingFingerprints)
        },
        onCancel = {
          completeResetMetric(MetricOutcome.UserCanceled)
          props.onCancel()
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
  private fun handleEnrollingFingerprints(props: ResetFingerprintsProps): ScreenModel {
    val appGlobalAuthPublicKey = props.account.keybox.activeAppKeyBundle.authKey

    return pairNewHardwareUiStateMachine.model(
      PairNewHardwareProps(
        request = PairNewHardwareProps.Request.Ready(
          appGlobalAuthPublicKey = appGlobalAuthPublicKey,
          onSuccess = { response ->
            completeResetMetric(MetricOutcome.Succeeded)
            props.onComplete()
          }
        ),
        onExit = {
          completeResetMetric(MetricOutcome.UserCanceled)
          props.onCancel()
        },
        segment = ResetFingerprintsSegment,
        eventTrackerContext = PairHardwareEventTrackerScreenIdContext.RESET_FINGERPRINTS,
        screenPresentationStyle = ScreenPresentationStyle.Modal,
        isResettingFingerprints = true
      )
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
        completeResetMetric(MetricOutcome.UserCanceled)
        props.onCancel()
      }.onFailure { error ->
        logError { "Failed to cancel fingerprint reset: $error" }
        completeResetMetric(MetricOutcome.Failed)
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
    val errorBodyModel = when (state) {
      is ResetFingerprintsUiState.Error.CreatePrivilegedActionError ->
        ResetFingerprintsErrorBodyModel.CreatePrivilegedActionError(state.error)
      is ResetFingerprintsUiState.Error.CompletePrivilegedActionError ->
        ResetFingerprintsErrorBodyModel.CompletePrivilegedActionError(state.error)
      is ResetFingerprintsUiState.Error.GenericError ->
        ResetFingerprintsErrorBodyModel.GenericError(
          title = state.title,
          message = state.message,
          cause = state.cause,
          eventTrackerScreenId = state.eventTrackerScreenId
        )
    }

    return ScreenModel(
      body = errorBodyModel.toFormBodyModel(
        onRetry = { updateState(ResetFingerprintsUiState.ShowingConfirmation()) },
        onCancel = props.onCancel
      ),
      presentationStyle = ScreenPresentationStyle.Modal
    )
  }

  private fun completeResetMetric(outcome: MetricOutcome) {
    metricTrackerService.completeMetric(
      metricDefinition = FingerprintResetMetricDefinition,
      outcome = outcome
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
    object CreatingGrantRequestViaNfc : ResetFingerprintsUiState

    /**
     * Showing the progress of the fingerprint reset, indicating the delay & notify period.
     */
    data class ShowingDelayAndNotifyProgress(
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
     * Represents the state when the delay and notify period is complete,
     * and the grant is ready to be retrieved.
     */
    data class DelayAndNotifyComplete(
      val actionId: String,
      val completionToken: String,
      val cancellationToken: String,
      val bottomSheetModel: SheetModel? = null,
    ) : ResetFingerprintsUiState

    /**
     * Completing the fingerprint reset by fetching the grant and providing to the hardware.
     */
    data class RequestingSignedGrantFromServer(
      val actionId: String,
      val completionToken: String,
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

    /**
     * Represents the state when the fingerprint enrollment is in progress.
     */
    object EnrollingFingerprints : ResetFingerprintsUiState

    /**
     * Represents the state when the grant is being provided via NFC.
     */
    data class ProvidingGrantViaNfc(
      val actionId: String,
      val grant: Grant,
    ) : ResetFingerprintsUiState
  }
}

internal object ResetFingerprintsSegment : AppSegment {
  override val id: String = "ResetFingerprintsFlow"
}
