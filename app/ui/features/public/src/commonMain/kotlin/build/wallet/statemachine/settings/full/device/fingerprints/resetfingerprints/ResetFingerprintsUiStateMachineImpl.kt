package build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints

import androidx.compose.runtime.*
import build.wallet.Progress
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.coroutines.flow.launchTicker
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

@BitkeyInject(ActivityScope::class)
class ResetFingerprintsUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val clock: Clock,
  private val durationFormatter: DurationFormatter,
) : ResetFingerprintsUiStateMachine {
  @Composable
  override fun model(props: ResetFingerprintsProps): ScreenModel {
    var uiState: ResetFingerprintsUiState by remember {
      mutableStateOf(ResetFingerprintsUiState.ShowingConfirmation())
    }

    return when (val state = uiState) {
      is ResetFingerprintsUiState.ShowingConfirmation -> {
        ScreenModel(
          body = ResetFingerprintsConfirmationBodyModel(
            onClose = props.onCancel,
            onConfirmReset = {
              val onDismiss = {
                uiState = ResetFingerprintsUiState.ShowingConfirmation()
              }
              uiState = state.copy(
                bottomSheetModel = ResetFingerprintsConfirmationSheetModel(
                  onDismiss = onDismiss,
                  onConfirmReset = {
                    uiState = ResetFingerprintsUiState.WaitingForNfc
                  }
                ).asSheetModalScreen(onClosed = onDismiss)
              )
            }
          ),
          bottomSheetModel = state.bottomSheetModel,
          presentationStyle = ScreenPresentationStyle.Modal
        )
      }

      is ResetFingerprintsUiState.WaitingForNfc -> {
        nfcSessionUIStateMachine.model(
          props = NfcSessionUIStateMachineProps<ResetFingerprintsNfcResult>(
            session = { session, commands ->
              // TODO: implement NFC session logic
              ResetFingerprintsNfcResult.Success
            },
            onSuccess = { result ->
              when (result) {
                ResetFingerprintsNfcResult.Success -> {
                  val now = clock.now()
                  uiState = ResetFingerprintsUiState.ShowingProgress(
                    startTime = now,
                    endTime = now + 7.days
                  )
                }
              }
            },
            onCancel = {
              uiState = ResetFingerprintsUiState.ShowingConfirmation()
            },
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            eventTrackerContext = NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS
          )
        )
      }

      is ResetFingerprintsUiState.ShowingProgress -> {
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
          }
        }

        ScreenModel(
          body = AppDelayNotifyInProgressBodyModel(
            headline = "Fingerprint reset in progress...",
            delayInfoText = "You’ll be able to add new fingerprints at the end of the 7-day security period.",
            cancelText = "Cancel reset",
            durationTitle = formattedDuration,
            progress = progress,
            remainingDelayPeriod = remainingDelayPeriod,
            onExit = props.onCancel,
            onStopRecovery = props.onCancel
          ),
          presentationStyle = ScreenPresentationStyle.Modal
        )
      }

      is ResetFingerprintsUiState.Finishing -> {
        ScreenModel(
          body = FinishResetFingerprintsBodyModel(
            onClose = props.onCancel,
            onConfirmReset = {
              // TODO: W-11410 implement reset logic
            },
            onCancelReset = {
              // TODO: W-11409 implement cancel logic
            }
          )
        )
      }
    }
  }

  private sealed interface ResetFingerprintsUiState {
    /**
     * Showing the confirmation dialog before proceeding with the reset.
     */
    data class ShowingConfirmation(
      val bottomSheetModel: SheetModel? = null,
    ) : ResetFingerprintsUiState

    /**
     * Waiting for the user to tap their locked device to proceed with the reset.
     */
    object WaitingForNfc : ResetFingerprintsUiState

    /**
     * Showing the progress of the fingerprint reset, indicating the delay & notify period.
     */
    data class ShowingProgress(
      val startTime: Instant,
      val endTime: Instant,
    ) : ResetFingerprintsUiState

    /**
     * Finishing the reset process, indicating that the user can now reset their fingerprints.
     */
    object Finishing : ResetFingerprintsUiState
  }
}
