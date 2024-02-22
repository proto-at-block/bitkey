package build.wallet.statemachine.account.create.full.hardware

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.v1.Action.ACTION_HW_FINGERPRINT_COMPLETE
import build.wallet.analytics.v1.Action.ACTION_HW_ONBOARDING_FINGERPRINT
import build.wallet.analytics.v1.Action.ACTION_HW_ONBOARDING_OPEN
import build.wallet.logging.LogLevel.Error
import build.wallet.logging.log
import build.wallet.nfc.transaction.PairingTransactionProvider
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrollmentRestarted
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintNotEnrolled
import build.wallet.nfc.transaction.StartFingerprintEnrollmentTransactionProvider
import build.wallet.statemachine.core.ScreenColorMode
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiProps
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiStateMachine

class PairNewHardwareUiStateMachineImpl(
  private val eventTracker: EventTracker,
  private val pairingTransactionProvider: PairingTransactionProvider,
  private val startFingerprintEnrollmentTransactionProvider:
    StartFingerprintEnrollmentTransactionProvider,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val helpCenterUiStateMachine: HelpCenterUiStateMachine,
) : PairNewHardwareUiStateMachine {
  @Composable
  override fun model(props: PairNewHardwareProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.ShowingActivationInstructionsUiState()) }

    // Always show the [PairNewHardwareBodyModel] as full screens
    val pairNewHardwareBodyModelPresentationStyle =
      when (props.screenPresentationStyle) {
        ScreenPresentationStyle.Modal -> ScreenPresentationStyle.ModalFullScreen
        else -> ScreenPresentationStyle.RootFullScreen
      }

    return when (val s = state) {
      is State.ShowingActivationInstructionsUiState ->
        ScreenModel(
          body =
            ActivationInstructionsBodyModel(
              onContinue =
                props.onSuccess?.let {
                  // Only continue if the props gave us a success callback, otherwise we're still loading.
                  { state = State.ShowingStartFingerprintEnrollmentInstructionsUiState() }
                },
              onBack = props.onExit,
              isNavigatingBack = s.isNavigatingBack,
              eventTrackerScreenIdContext = props.eventTrackerContext
            ),
          presentationStyle = pairNewHardwareBodyModelPresentationStyle,
          colorMode = ScreenColorMode.Dark
        )

      is State.ShowingStartFingerprintEnrollmentInstructionsUiState ->
        ScreenModel(
          body =
            StartFingerprintEnrollmentInstructionsBodyModel(
              onButtonClick = { state = State.StartFingerprintEnrollmentViaNfcUiState },
              onBack = { state = State.ShowingActivationInstructionsUiState(isNavigatingBack = true) },
              isNavigatingBack = s.isNavigatingBack,
              eventTrackerScreenIdContext = props.eventTrackerContext
            ),
          presentationStyle = pairNewHardwareBodyModelPresentationStyle,
          colorMode = ScreenColorMode.Dark
        )

      is State.StartFingerprintEnrollmentViaNfcUiState -> {
        LaunchedEffect("pairing-event") {
          eventTracker.track(action = ACTION_HW_ONBOARDING_OPEN)
        }
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            transaction =
              startFingerprintEnrollmentTransactionProvider(
                onCancel = {
                  state = State.ShowingStartFingerprintEnrollmentInstructionsUiState(isNavigatingBack = true)
                },
                onSuccess = { state = State.ShowingCompleteFingerprintEnrollmentInstructionsUiState() },
                isHardwareFake = props.keyboxConfig.isHardwareFake
              ),
            screenPresentationStyle = props.screenPresentationStyle,
            eventTrackerContext = NfcEventTrackerScreenIdContext.PAIR_NEW_HW_ACTIVATION,
            onInauthenticHardware = { state = State.ShowingHelpCenter }
          )
        )
      }

      is State.ShowingCompleteFingerprintEnrollmentInstructionsUiState ->
        HardwareFingerprintEnrollmentScreenModel(
          showingIncompleteEnrollmentError = s.showingIncompleteEnrollmentError,
          incompleteEnrollmentErrorOnPrimaryButtonClick = {
            state = s.copy(showingIncompleteEnrollmentError = false)
          },
          onSaveFingerprint = {
            state = State.CompleteFingerprintEnrollmentViaNfcUiState
          },
          onErrorOverlayClosed = {
            state = s.copy(showingIncompleteEnrollmentError = false)
          },
          onBack = {
            state = State.ShowingStartFingerprintEnrollmentInstructionsUiState(
              isNavigatingBack = true
            )
          },
          eventTrackerScreenIdContext = props.eventTrackerContext,
          isNavigatingBack = s.isNavigatingBack,
          presentationStyle = pairNewHardwareBodyModelPresentationStyle
        )

      is State.CompleteFingerprintEnrollmentViaNfcUiState -> {
        LaunchedEffect("fingerprint-event") {
          eventTracker.track(action = ACTION_HW_ONBOARDING_FINGERPRINT)
        }
        // activate hardware
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            transaction =
              pairingTransactionProvider(
                networkType = props.keyboxConfig.networkType,
                onSuccess = { response ->
                  when (response) {
                    is FingerprintEnrolled -> {
                      eventTracker.track(action = ACTION_HW_FINGERPRINT_COMPLETE)
                      props.onSuccess?.let {
                        it(response)
                      } ?: run {
                        log(Error) { "Unexpected null onSuccess when pairing hardware" }
                      }
                    }

                    FingerprintNotEnrolled -> {
                      state =
                        State.ShowingCompleteFingerprintEnrollmentInstructionsUiState(
                          showingIncompleteEnrollmentError = true
                        )
                    }

                    FingerprintEnrollmentRestarted -> {
                      state =
                        State.ShowingCompleteFingerprintEnrollmentInstructionsUiState(
                          showingIncompleteEnrollmentError = true
                        )
                    }
                  }
                },
                onCancel = {
                  state = State.ShowingCompleteFingerprintEnrollmentInstructionsUiState()
                },
                isHardwareFake = props.keyboxConfig.isHardwareFake
              ),
            screenPresentationStyle = props.screenPresentationStyle,
            eventTrackerContext = NfcEventTrackerScreenIdContext.PAIR_NEW_HW_FINGERPRINT,
            onInauthenticHardware = {
              // Inauthentic hardware should be caught on first tap. Instead of ignoring this error,
              // we'll log that it happened and reject the hardware -- even though this state
              // should be unreachable.
              log(Error) {
                "Detected inauthentic hardware in CompleteFingerprintEnrollmentViaNfcUiState," +
                  "which shouldn't happen"
              }
              state = State.ShowingHelpCenter
            }
          )
        )
      }

      is State.ShowingHelpCenter -> {
        helpCenterUiStateMachine.model(
          props =
            HelpCenterUiProps(
              onBack = props.onExit
            )
        ).copy(presentationStyle = props.screenPresentationStyle)
      }
    }
  }

  private sealed interface State {
    /**
     * Showing instructions for how to activate the new hardware.
     */
    data class ShowingActivationInstructionsUiState(
      val isNavigatingBack: Boolean = false,
    ) : State

    /**
     * Showing instructions for how to start fingerprint enrollment.
     */
    data class ShowingStartFingerprintEnrollmentInstructionsUiState(
      val isNavigatingBack: Boolean = false,
    ) : State

    /**
     * Showing NFC screen to start fingerprint enrollment.
     */
    data object StartFingerprintEnrollmentViaNfcUiState : State

    /**
     * Showing instructions for how to complete hardware fingerprint enrollment using NFC.
     */
    data class ShowingCompleteFingerprintEnrollmentInstructionsUiState(
      val showingIncompleteEnrollmentError: Boolean = false,
      val isNavigatingBack: Boolean = false,
    ) : State

    /**
     * Showing NFC screen, waiting for customer to complete instructions and tap the hardware to
     * confirm fingerprint enrollment.
     */
    data object CompleteFingerprintEnrollmentViaNfcUiState : State

    data object ShowingHelpCenter : State
  }
}
