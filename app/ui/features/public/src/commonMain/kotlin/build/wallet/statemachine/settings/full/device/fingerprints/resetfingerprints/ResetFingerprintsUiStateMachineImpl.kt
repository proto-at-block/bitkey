package build.wallet.statemachine.settings.full.device.fingerprints.resetfingerprints

import androidx.compose.runtime.*
import build.wallet.Progress
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.coroutines.flow.launchTicker
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
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
import io.ktor.utils.io.core.toByteArray
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
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
              val grantRequest = commands.getGrantRequest(session, GrantAction.FINGERPRINT_RESET)
              ResetFingerprintsNfcResult.GrantRequestRetrieved(grantRequest)
            },
            onSuccess = { result ->
              when (result) {
                is ResetFingerprintsNfcResult.GrantRequestRetrieved -> {
                  // TODO: pass grant request to server
                  val now = clock.now()
                  uiState = ResetFingerprintsUiState.ShowingProgress(
                    startTime = now,
                    endTime = now + 7.days,
                    grantRequest = result.grantRequest
                  )
                }
                else -> {
                  uiState = ResetFingerprintsUiState.ShowingConfirmation()
                }
              }
            },
            onCancel = {
              uiState = ResetFingerprintsUiState.ShowingConfirmation()
            },
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            eventTrackerContext = NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS,
            needsAuthentication = false,
            hardwareVerification = NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
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
            if (remainingDelayPeriod.isNegative() || remainingDelayPeriod == Duration.ZERO) {
              // TODO: fetch grant from server
              val mockGrant = Grant(
                version = 0x01,
                serializedRequest = "mockSerializedRequest".toByteArray(),
                signature = "mockSignature".toByteArray()
              )
              uiState = ResetFingerprintsUiState.ReadyToProvideGrant(mockGrant)
            }
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

      is ResetFingerprintsUiState.ReadyToProvideGrant -> {
        nfcSessionUIStateMachine.model(
          props = NfcSessionUIStateMachineProps(
            session = { session, commands ->
              val success = commands.provideGrant(session, state.grant)
              if (success) {
                ResetFingerprintsNfcResult.ProvideGrantSuccess
              } else {
                ResetFingerprintsNfcResult.ProvideGrantFailure
              }
            },
            onSuccess = { result ->
              when (result) {
                is ResetFingerprintsNfcResult.ProvideGrantSuccess -> {
                  uiState = ResetFingerprintsUiState.Finishing
                }
                is ResetFingerprintsNfcResult.ProvideGrantFailure -> {
                  uiState = ResetFingerprintsUiState.ShowingConfirmation()
                }
                else -> {
                  uiState = ResetFingerprintsUiState.ShowingConfirmation()
                }
              }
            },
            onCancel = {
              uiState = ResetFingerprintsUiState.ShowingConfirmation()
            },
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            eventTrackerContext = NfcEventTrackerScreenIdContext.RESET_FINGERPRINTS,
            needsAuthentication = false,
            hardwareVerification = NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
          )
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
      val grantRequest: GrantRequest,
    ) : ResetFingerprintsUiState

    /**
     * Ready to provide the server-signed grant to the hardware after D+N.
     */
    data class ReadyToProvideGrant(
      val grant: Grant,
    ) : ResetFingerprintsUiState

    /**
     * Finishing the reset process, indicating that the user can now reset their fingerprints.
     */
    object Finishing : ResetFingerprintsUiState
  }
}
