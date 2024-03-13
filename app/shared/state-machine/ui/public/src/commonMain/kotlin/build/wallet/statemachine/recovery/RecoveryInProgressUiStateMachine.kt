package build.wallet.statemachine.recovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_APP_DELAY_NOTIFY_LOST_APP_TAPPED_STOP
import build.wallet.analytics.v1.Action.ACTION_APP_DELAY_NOTIFY_LOST_HARDWARE_TAPPED_STOP
import build.wallet.analytics.v1.Action.ACTION_APP_DELAY_NOTIFY_PENDING_LOST_APP_CANCEL
import build.wallet.analytics.v1.Action.ACTION_APP_DELAY_NOTIFY_PENDING_LOST_APP_DISMISS_STOP_RECOVERY
import build.wallet.analytics.v1.Action.ACTION_APP_DELAY_NOTIFY_PENDING_LOST_HARDWARE_CANCEL
import build.wallet.analytics.v1.Action.ACTION_APP_DELAY_NOTIFY_PENDING_LOST_HARDWARE_DISMISS_STOP_RECOVERY
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.money.currency.FiatCurrency
import build.wallet.recovery.getEventId
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.NetworkErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.AwaitingProofOfPossessionForCancellationData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CancellingData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.VerifyingNotificationCommsForCancellationData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.WaitingForRecoveryDelayPeriodData
import build.wallet.statemachine.recovery.inprogress.completing.CompletingRecoveryUiProps
import build.wallet.statemachine.recovery.inprogress.completing.CompletingRecoveryUiStateMachine
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.statemachine.recovery.inprogress.waiting.CancelRecoveryAlertModel
import build.wallet.statemachine.recovery.inprogress.waiting.HardwareDelayNotifyInProgressScreenModel
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiProps
import build.wallet.statemachine.recovery.verification.RecoveryNotificationVerificationUiStateMachine
import build.wallet.time.DurationFormatter
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/**
 * A UI state machine when the customer has engaged with the recovery process and is waiting
 * for delay period or completing recovery.
 */
interface RecoveryInProgressUiStateMachine : StateMachine<RecoveryInProgressUiProps, ScreenModel>

data class RecoveryInProgressUiProps(
  val presentationStyle: ScreenPresentationStyle,
  val recoveryInProgressData: RecoveryInProgressData,
  val fullAccountConfig: FullAccountConfig,
  val fiatCurrency: FiatCurrency,
  val onExit: (() -> Unit)? = null,
)

class RecoveryInProgressUiStateMachineImpl(
  private val completingRecoveryUiStateMachine: CompletingRecoveryUiStateMachine,
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val durationFormatter: DurationFormatter,
  private val clock: Clock,
  private val eventTracker: EventTracker,
  private val recoveryNotificationVerificationUiStateMachine:
    RecoveryNotificationVerificationUiStateMachine,
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
          while (true) {
            remainingDelayPeriod = recoveryInProgressData.remainingDelayPeriod(clock)
            delay(DurationFormatter.MINIMUM_DURATION_WORD_FORMAT_UPDATE)
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
              alertModel =
                if (confirmingCancellation) {
                  CancelRecoveryAlertModel(
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
              alertModel =
                if (confirmingCancellation) {
                  CancelRecoveryAlertModel(
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
            fiatCurrency = props.fiatCurrency,
            onExit = props.onExit,
            isHardwareFake = props.fullAccountConfig.isHardwareFake
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
            fullAccountConfig = props.fullAccountConfig,
            appAuthKey = recoveryInProgressData.appAuthKey,
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
              recoveryNotificationVerificationData = recoveryInProgressData.data,
              lostFactor = recoveryInProgressData.lostFactor
            )
        )

      is RecoveryInProgressData.FailedToCancelRecoveryData ->
        CancelConflictingRecoveryErrorScreenModel(
          id =
            recoveryInProgressData.recoveredFactor.getEventId(
              DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_CANCELLATION_ERROR,
              HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_CANCELLATION_ERROR
            ),
          isConnectivityError = recoveryInProgressData.isNetworkError,
          onDoneClicked = recoveryInProgressData.onAcknowledge
        )
    }
  }

  private fun CancelConflictingRecoveryErrorScreenModel(
    id: EventTrackerScreenId,
    isConnectivityError: Boolean,
    onDoneClicked: () -> Unit,
  ): ScreenModel =
    NetworkErrorFormBodyModel(
      title = "We couldn’t cancel your recovery.",
      isConnectivityError = isConnectivityError,
      onBack = onDoneClicked,
      eventTrackerScreenId = id
    ).asRootScreen()
}
