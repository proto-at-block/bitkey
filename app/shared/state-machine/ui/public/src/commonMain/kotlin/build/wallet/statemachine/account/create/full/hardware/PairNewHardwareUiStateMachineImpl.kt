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
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrollmentStarted
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintNotEnrolled
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.CompleteFingerprintEnrollmentViaNfcUiState
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.ShowingActivationInstructionsUiState
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.ShowingCompleteFingerprintEnrollmentInstructionsUiState
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.ShowingHelpCenter
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.ShowingStartFingerprintEnrollmentInstructionsUiState
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.StartFingerprintEnrollmentViaNfcUiState
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
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val helpCenterUiStateMachine: HelpCenterUiStateMachine,
) : PairNewHardwareUiStateMachine {
  @Composable
  override fun model(props: PairNewHardwareProps): ScreenModel {
    var state: State by remember { mutableStateOf(ShowingActivationInstructionsUiState()) }

    // Always show the [PairNewHardwareBodyModel] as full screens
    val pairNewHardwareBodyModelPresentationStyle =
      when (props.screenPresentationStyle) {
        ScreenPresentationStyle.Modal -> ScreenPresentationStyle.ModalFullScreen
        else -> ScreenPresentationStyle.RootFullScreen
      }

    return when (val s = state) {
      is ShowingActivationInstructionsUiState ->
        ScreenModel(
          body = ActivationInstructionsBodyModel(
            onContinue = when (props.request) {
              // Only continue if the props gave us a ready request
              is PairNewHardwareProps.Request.Ready -> {
                {
                  state = ShowingStartFingerprintEnrollmentInstructionsUiState(props.request)
                }
              }
              // Otherwise we're still loading
              else -> null
            },
            onBack = props.onExit,
            isNavigatingBack = s.isNavigatingBack,
            eventTrackerScreenIdContext = props.eventTrackerContext
          ),
          presentationStyle = pairNewHardwareBodyModelPresentationStyle,
          colorMode = ScreenColorMode.Dark
        )

      is ShowingStartFingerprintEnrollmentInstructionsUiState ->
        ScreenModel(
          body = StartFingerprintEnrollmentInstructionsBodyModel(
            onButtonClick = { state = StartFingerprintEnrollmentViaNfcUiState(s.request) },
            onBack = {
              state = ShowingActivationInstructionsUiState(isNavigatingBack = true)
            },
            isNavigatingBack = s.isNavigatingBack,
            eventTrackerScreenIdContext = props.eventTrackerContext
          ),
          presentationStyle = pairNewHardwareBodyModelPresentationStyle,
          colorMode = ScreenColorMode.Dark
        )

      is StartFingerprintEnrollmentViaNfcUiState -> {
        LaunchedEffect("pairing-event") {
          eventTracker.track(action = ACTION_HW_ONBOARDING_OPEN)
        }

        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            transaction = pairingTransactionProvider(
              onCancel = {
                state =
                  ShowingStartFingerprintEnrollmentInstructionsUiState(
                    s.request,
                    isNavigatingBack = true
                  )
              },
              onSuccess = {
                when (it) {
                  is FingerprintEnrolled -> {
                    eventTracker.track(action = ACTION_HW_FINGERPRINT_COMPLETE)
                    s.request.onSuccess(it)
                  }
                  FingerprintEnrollmentStarted,
                  FingerprintNotEnrolled,
                  -> {
                    state = ShowingCompleteFingerprintEnrollmentInstructionsUiState(s.request)
                  }
                }
              },
              isHardwareFake = s.request.fullAccountConfig.isHardwareFake,
              networkType = s.request.fullAccountConfig.bitcoinNetworkType,
              appGlobalAuthPublicKey = s.request.appGlobalAuthPublicKey
            ),
            screenPresentationStyle = props.screenPresentationStyle,
            segment = props.segment,
            actionDescription = "Pairing new hardware",
            eventTrackerContext = NfcEventTrackerScreenIdContext.PAIR_NEW_HW_ACTIVATION,
            onInauthenticHardware = { state = ShowingHelpCenter }
          )
        )
      }

      is ShowingCompleteFingerprintEnrollmentInstructionsUiState ->
        HardwareFingerprintEnrollmentScreenModel(
          showingIncompleteEnrollmentError = s.showingIncompleteEnrollmentError,
          incompleteEnrollmentErrorOnPrimaryButtonClick = {
            state = s.copy(showingIncompleteEnrollmentError = false)
          },
          onSaveFingerprint = {
            state = CompleteFingerprintEnrollmentViaNfcUiState(s.request)
          },
          onErrorOverlayClosed = {
            state = s.copy(showingIncompleteEnrollmentError = false)
          },
          onBack = {
            state = ShowingStartFingerprintEnrollmentInstructionsUiState(
              s.request,
              isNavigatingBack = true
            )
          },
          eventTrackerScreenIdContext = props.eventTrackerContext,
          isNavigatingBack = s.isNavigatingBack,
          presentationStyle = pairNewHardwareBodyModelPresentationStyle,
          headline = "Set up your fingerprint",
          instructions = "Place your finger on the sensor until you see a blue light." +
            " Repeat this until the device has a solid green light." +
            " Once done, press the button below to save your fingerprint."
        )

      is CompleteFingerprintEnrollmentViaNfcUiState -> {
        LaunchedEffect("fingerprint-event") {
          eventTracker.track(action = ACTION_HW_ONBOARDING_FINGERPRINT)
        }
        // activate hardware
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            transaction = pairingTransactionProvider(
              networkType = s.request.fullAccountConfig.bitcoinNetworkType,
              appGlobalAuthPublicKey = s.request.appGlobalAuthPublicKey,
              onSuccess = { response ->
                when (response) {
                  is FingerprintEnrolled -> {
                    eventTracker.track(action = ACTION_HW_FINGERPRINT_COMPLETE)
                    s.request.onSuccess(response)
                  }

                  FingerprintNotEnrolled -> {
                    state =
                      ShowingCompleteFingerprintEnrollmentInstructionsUiState(
                        s.request,
                        showingIncompleteEnrollmentError = true
                      )
                  }

                  FingerprintEnrollmentStarted -> {
                    state = ShowingCompleteFingerprintEnrollmentInstructionsUiState(
                      s.request,
                      showingIncompleteEnrollmentError = true
                    )
                  }
                }
              },
              onCancel = {
                state = ShowingCompleteFingerprintEnrollmentInstructionsUiState(s.request)
              },
              isHardwareFake = s.request.fullAccountConfig.isHardwareFake
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
              state = ShowingHelpCenter
            }
          )
        )
      }

      is ShowingHelpCenter -> {
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
      val request: PairNewHardwareProps.Request.Ready,
      val isNavigatingBack: Boolean = false,
    ) : State

    /**
     * Showing NFC screen to start fingerprint enrollment.
     */
    data class StartFingerprintEnrollmentViaNfcUiState(
      val request: PairNewHardwareProps.Request.Ready,
    ) : State

    /**
     * Showing instructions for how to complete hardware fingerprint enrollment using NFC.
     */
    data class ShowingCompleteFingerprintEnrollmentInstructionsUiState(
      val request: PairNewHardwareProps.Request.Ready,
      val showingIncompleteEnrollmentError: Boolean = false,
      val isNavigatingBack: Boolean = false,
    ) : State

    /**
     * Showing NFC screen, waiting for customer to complete instructions and tap the hardware to
     * confirm fingerprint enrollment.
     */
    data class CompleteFingerprintEnrollmentViaNfcUiState(
      val request: PairNewHardwareProps.Request.Ready,
    ) : State

    data object ShowingHelpCenter : State
  }
}
