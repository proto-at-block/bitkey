package build.wallet.statemachine.recovery

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.analytics.v1.Action.*
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.coroutines.flow.launchTicker
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.recovery.getEventId
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.*
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.Required
import build.wallet.statemachine.recovery.inprogress.completing.CompletingRecoveryUiProps
import build.wallet.statemachine.recovery.inprogress.completing.CompletingRecoveryUiStateMachine
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.statemachine.recovery.inprogress.waiting.HardwareDelayNotifyInProgressScreenModel
import build.wallet.statemachine.recovery.inprogress.waiting.cancelRecoveryAlertModel
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiProps
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachine
import build.wallet.statemachine.root.RemainingRecoveryDelayWordsUpdateFrequency
import build.wallet.time.DurationFormatter
import kotlinx.datetime.Clock

/**
 * A UI state machine when the customer has engaged with the recovery process and is waiting
 * for delay period or completing recovery.
 */
interface RecoveryInProgressUiStateMachine : StateMachine<RecoveryInProgressUiProps, ScreenModel>

data class RecoveryInProgressUiProps(
  val presentationStyle: ScreenPresentationStyle,
  val recoveryInProgressData: RecoveryInProgressData,
  val onExit: (() -> Unit)? = null,
  val onComplete: (() -> Unit)? = null,
)

@BitkeyInject(ActivityScope::class)
class RecoveryInProgressUiStateMachineImpl(
  private val completingRecoveryUiStateMachine: CompletingRecoveryUiStateMachine,
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val durationFormatter: DurationFormatter,
  private val clock: Clock,
  private val eventTracker: EventTracker,
  private val recoveryNotificationVerificationUiStateMachine:
    RecoveryNotificationVerificationUiStateMachine,
  private val remainingRecoveryDelayWordsUpdateFrequency:
    RemainingRecoveryDelayWordsUpdateFrequency,
) : RecoveryInProgressUiStateMachine {
  @Composable
  override fun model(props: RecoveryInProgressUiProps): ScreenModel {
    return when (val recoveryInProgressData = props.recoveryInProgressData) {
      is WaitingForRecoveryDelayPeriodData -> {
        var confirmingCancellation by remember { mutableStateOf(false) }
        var remainingDelayPeriod by remember {
          mutableStateOf(recoveryInProgressData.remainingDelayPeriod(clock))
        }
        // Derive formatted delay period when the duration state is updated.
        val remainingDelayInWords by remember(remainingDelayPeriod) {
          derivedStateOf {
            durationFormatter.formatWithWords(remainingDelayPeriod)
          }
        }

        // Periodically update [remainingDelayPeriod] so that the formatted words update accordingly
        LaunchedEffect("update-delay-progress") {
          launchTicker(remainingRecoveryDelayWordsUpdateFrequency.value) {
            remainingDelayPeriod = recoveryInProgressData.remainingDelayPeriod(clock)
          }
        }

        when (recoveryInProgressData.factorToRecover) {
          App ->
            AppDelayNotifyInProgressBodyModel(
              onStopRecovery = {
                eventTracker.track(ACTION_APP_DELAY_NOTIFY_LOST_APP_TAPPED_STOP)
                confirmingCancellation = true
              },
              durationTitle = remainingDelayInWords,
              progress = recoveryInProgressData.delayPeriodProgress(clock),
              remainingDelayPeriod = remainingDelayPeriod,
              onExit = props.onExit
            ).asScreen(
              presentationStyle = props.presentationStyle,
              alertModel = if (confirmingCancellation) {
                cancelRecoveryAlertModel(
                  onConfirm = {
                    eventTracker.track(ACTION_APP_DELAY_NOTIFY_PENDING_LOST_APP_CANCEL)
                    recoveryInProgressData.cancel()
                    confirmingCancellation = false
                  },
                  onDismiss = {
                    eventTracker.track(
                      ACTION_APP_DELAY_NOTIFY_PENDING_LOST_APP_DISMISS_STOP_RECOVERY
                    )
                    confirmingCancellation = false
                  }
                )
              } else {
                null
              }
            )

          Hardware ->
            HardwareDelayNotifyInProgressScreenModel(
              onCancelRecovery = {
                eventTracker.track(ACTION_APP_DELAY_NOTIFY_LOST_HARDWARE_TAPPED_STOP)
                confirmingCancellation = true
              },
              durationTitle = remainingDelayInWords,
              progress = recoveryInProgressData.delayPeriodProgress(clock),
              remainingDelayPeriod = remainingDelayPeriod,
              onExit = props.onExit ?: {} // TODO(W-3276): handle
            ).asScreen(
              presentationStyle = props.presentationStyle,
              alertModel = if (confirmingCancellation) {
                cancelRecoveryAlertModel(
                  onConfirm = {
                    eventTracker.track(
                      ACTION_APP_DELAY_NOTIFY_PENDING_LOST_HARDWARE_CANCEL
                    )
                    recoveryInProgressData.cancel()
                    confirmingCancellation = false
                  },
                  onDismiss = {
                    eventTracker.track(
                      ACTION_APP_DELAY_NOTIFY_PENDING_LOST_HARDWARE_DISMISS_STOP_RECOVERY
                    )
                    confirmingCancellation = false
                  }
                )
              } else {
                null
              }
            )
        }
      }

      is CompletingRecoveryData ->
        completingRecoveryUiStateMachine.model(
          CompletingRecoveryUiProps(
            presentationStyle = props.presentationStyle,
            completingRecoveryData = recoveryInProgressData,
            onExit = props.onExit,
            onComplete = props.onComplete
          )
        )

      is AwaitingProofOfPossessionForCancellationData ->
        proofOfPossessionNfcStateMachine.model(
          ProofOfPossessionNfcProps(
            request =
              Request.HwKeyProof(
                onSuccess = {
                  recoveryInProgressData.addHardwareProofOfPossession(it)
                }
              ),
            fullAccountId = recoveryInProgressData.fullAccountId,
            appAuthKey = recoveryInProgressData.appAuthKey,
            hardwareVerification = Required(useRecoveryPubKey = true),
            screenPresentationStyle = Modal, // TODO Validate this is correct?
            onBack = recoveryInProgressData.rollback,
            onTokenRefresh = null,
            onTokenRefreshError = null
          )
        )

      is CancellingData -> {
        LoadingBodyModel(
          id =
            recoveryInProgressData.recoveredFactor.getEventId(
              DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_CANCELLATION,
              HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_CANCELLATION
            )
        ).asScreen(
          presentationStyle = props.presentationStyle
        )
      }

      is VerifyingNotificationCommsForCancellationData ->
        recoveryNotificationVerificationUiStateMachine.model(
          props =
            RecoveryNotificationVerificationUiProps(
              fullAccountId = recoveryInProgressData.fullAccountId,
              localLostFactor = recoveryInProgressData.lostFactor,
              hwFactorProofOfPossession = null,
              onComplete = recoveryInProgressData.onComplete,
              onRollback = recoveryInProgressData.onRollback
            )
        )

      is FailedToCancelRecoveryData ->
        CancelConflictingRecoveryErrorScreenModel(
          id =
            recoveryInProgressData.recoveredFactor.getEventId(
              DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_CANCELLATION_ERROR,
              HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_CANCELLATION_ERROR
            ),
          isConnectivityError = recoveryInProgressData.isNetworkError,
          errorData = ErrorData(
            segment = when (recoveryInProgressData.recoveredFactor) {
              App -> RecoverySegment.DelayAndNotify.LostApp.Cancellation
              Hardware -> RecoverySegment.DelayAndNotify.LostHardware.Cancellation
            },
            actionDescription = "Cancelling conflicting recovery",
            cause = recoveryInProgressData.cause
          ),
          onDoneClicked = recoveryInProgressData.onAcknowledge
        )
    }
  }

  private fun CancelConflictingRecoveryErrorScreenModel(
    id: EventTrackerScreenId,
    isConnectivityError: Boolean,
    errorData: ErrorData,
    onDoneClicked: () -> Unit,
  ): ScreenModel =
    NetworkErrorFormBodyModel(
      title = "We couldn’t cancel your recovery.",
      isConnectivityError = isConnectivityError,
      onBack = onDoneClicked,
      errorData = errorData,
      eventTrackerScreenId = id
    ).asRootScreen()
}
