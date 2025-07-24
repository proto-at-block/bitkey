package build.wallet.statemachine.account.create.full.hardware

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import bitkey.firmware.HardwareUnlockInfoService
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.v1.Action.ACTION_HW_FINGERPRINT_COMPLETE
import build.wallet.analytics.v1.Action.ACTION_HW_ONBOARDING_FINGERPRINT
import build.wallet.analytics.v1.Action.ACTION_HW_ONBOARDING_OPEN
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.UnlockInfo
import build.wallet.logging.*
import build.wallet.nfc.transaction.PairingTransactionProvider
import build.wallet.nfc.transaction.PairingTransactionResponse
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrollmentStarted
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintNotEnrolled
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.CompleteFingerprintEnrollmentViaNfcUiState
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.ShowingActivationInstructionsUiState
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.ShowingCompleteFingerprintEnrollmentInstructionsUiState
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.ShowingHelpCenter
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.ShowingStartFingerprintEnrollmentInstructionsUiState
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.StartFingerprintEnrollmentViaNfcUiState
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiProps
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiStateMachine
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class PairNewHardwareUiStateMachineImpl(
  private val eventTracker: EventTracker,
  private val pairingTransactionProvider: PairingTransactionProvider,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val helpCenterUiStateMachine: HelpCenterUiStateMachine,
  private val hardwareUnlockInfoService: HardwareUnlockInfoService,
) : PairNewHardwareUiStateMachine {
  @Composable
  override fun model(props: PairNewHardwareProps): ScreenModel {
    val scope = rememberStableCoroutineScope()
    var state: State by remember { mutableStateOf(ShowingActivationInstructionsUiState()) }

    val pairNewHardwareBodyModelPresentationStyle = determinePresentationStyle(props.screenPresentationStyle)

    return when (val s = state) {
      is ShowingActivationInstructionsUiState ->
        handleActivationInstructions(s, props, pairNewHardwareBodyModelPresentationStyle) { state = it }

      is ShowingStartFingerprintEnrollmentInstructionsUiState ->
        handleStartFingerprintEnrollmentInstructions(s, props, pairNewHardwareBodyModelPresentationStyle) {
          state = it
        }

      is StartFingerprintEnrollmentViaNfcUiState ->
        handleStartFingerprintEnrollmentViaNfc(s, props, scope) { state = it }

      is ShowingCompleteFingerprintEnrollmentInstructionsUiState ->
        handleCompleteFingerprintEnrollmentInstructions(s, props, pairNewHardwareBodyModelPresentationStyle) {
          state = it
        }

      is CompleteFingerprintEnrollmentViaNfcUiState ->
        handleCompleteFingerprintEnrollmentViaNfc(s, props, scope) { state = it }

      is ShowingHelpCenter ->
        handleShowingHelpCenter(props)
    }
  }

  private fun determinePresentationStyle(
    screenPresentationStyle: ScreenPresentationStyle,
  ): ScreenPresentationStyle {
    // Always show the [PairNewHardwareBodyModel] as full screens
    return when (screenPresentationStyle) {
      ScreenPresentationStyle.Modal -> ScreenPresentationStyle.ModalFullScreen
      else -> ScreenPresentationStyle.RootFullScreen
    }
  }

  @Composable
  private fun handleActivationInstructions(
    state: ShowingActivationInstructionsUiState,
    props: PairNewHardwareProps,
    presentationStyle: ScreenPresentationStyle,
    updateState: (State) -> Unit,
  ): ScreenModel {
    return ScreenModel(
      body = ActivationInstructionsBodyModel(
        onContinue = when (props.request) {
          // Only continue if the props gave us a ready request
          is PairNewHardwareProps.Request.Ready -> {
            { updateState(ShowingStartFingerprintEnrollmentInstructionsUiState(props.request)) }
          }
          // Otherwise we're still loading
          else -> null
        },
        onBack = props.onExit,
        isNavigatingBack = state.isNavigatingBack,
        eventTrackerContext = props.eventTrackerContext
      ),
      presentationStyle = presentationStyle,
      themePreference = ThemePreference.Manual(Theme.DARK)
    )
  }

  @Composable
  private fun handleStartFingerprintEnrollmentInstructions(
    state: ShowingStartFingerprintEnrollmentInstructionsUiState,
    props: PairNewHardwareProps,
    presentationStyle: ScreenPresentationStyle,
    updateState: (State) -> Unit,
  ): ScreenModel {
    return ScreenModel(
      body = StartFingerprintEnrollmentInstructionsBodyModel(
        onButtonClick = { updateState(StartFingerprintEnrollmentViaNfcUiState(state.request)) },
        onBack = { updateState(ShowingActivationInstructionsUiState(isNavigatingBack = true)) },
        isNavigatingBack = state.isNavigatingBack,
        eventTrackerScreenIdContext = props.eventTrackerContext
      ),
      presentationStyle = presentationStyle,
      themePreference = ThemePreference.Manual(Theme.DARK)
    )
  }

  @Composable
  private fun handleStartFingerprintEnrollmentViaNfc(
    state: StartFingerprintEnrollmentViaNfcUiState,
    props: PairNewHardwareProps,
    scope: kotlinx.coroutines.CoroutineScope,
    updateState: (State) -> Unit,
  ): ScreenModel {
    LaunchedEffect("pairing-event") {
      eventTracker.track(action = ACTION_HW_ONBOARDING_OPEN)
    }

    return nfcSessionUIStateMachine.model(
      NfcSessionUIStateMachineProps(
        transaction = pairingTransactionProvider(
          onCancel = {
            updateState(
              ShowingStartFingerprintEnrollmentInstructionsUiState(
                state.request,
                isNavigatingBack = true
              )
            )
          },
          onSuccess = { response ->
            handleStartFingerprintEnrollmentSuccess(response, state, scope, updateState)
          },
          appGlobalAuthPublicKey = state.request.appGlobalAuthPublicKey,
          shouldLockHardware = true
        ),
        screenPresentationStyle = props.screenPresentationStyle,
        segment = props.segment,
        hardwareVerification = NotRequired,
        actionDescription = "Pairing new hardware",
        eventTrackerContext = NfcEventTrackerScreenIdContext.PAIR_NEW_HW_ACTIVATION,
        onInauthenticHardware = { updateState(ShowingHelpCenter) }
      )
    )
  }

  private fun handleStartFingerprintEnrollmentSuccess(
    response: PairingTransactionResponse,
    state: StartFingerprintEnrollmentViaNfcUiState,
    scope: kotlinx.coroutines.CoroutineScope,
    updateState: (State) -> Unit,
  ) {
    when (response) {
      is FingerprintEnrolled -> {
        scope.launch {
          hardwareUnlockInfoService.replaceAllUnlockInfo(unlockInfoList = UnlockInfo.ONBOARDING_DEFAULT)
          eventTracker.track(action = ACTION_HW_FINGERPRINT_COMPLETE)
          state.request.onSuccess(response)
        }
      }
      FingerprintEnrollmentStarted,
      FingerprintNotEnrolled,
      -> {
        updateState(ShowingCompleteFingerprintEnrollmentInstructionsUiState(state.request))
      }
    }
  }

  @Composable
  private fun handleCompleteFingerprintEnrollmentInstructions(
    state: ShowingCompleteFingerprintEnrollmentInstructionsUiState,
    props: PairNewHardwareProps,
    presentationStyle: ScreenPresentationStyle,
    updateState: (State) -> Unit,
  ): ScreenModel {
    return HardwareFingerprintEnrollmentScreenModel(
      showingIncompleteEnrollmentError = state.showingIncompleteEnrollmentError,
      incompleteEnrollmentErrorOnPrimaryButtonClick = {
        updateState(state.copy(showingIncompleteEnrollmentError = false))
      },
      onSaveFingerprint = {
        updateState(CompleteFingerprintEnrollmentViaNfcUiState(state.request))
      },
      onErrorOverlayClosed = {
        updateState(state.copy(showingIncompleteEnrollmentError = false))
      },
      onBack = {
        updateState(
          ShowingStartFingerprintEnrollmentInstructionsUiState(
            state.request,
            isNavigatingBack = true
          )
        )
      },
      eventTrackerContext = props.eventTrackerContext,
      isNavigatingBack = state.isNavigatingBack,
      presentationStyle = presentationStyle,
      headline = "Set up your first fingerprint",
      instructions = "Place your finger on the sensor until you see a blue light. Lift your" +
        " finger and repeat (15-20 times) adjusting your finger position slightly each time," +
        " until the light turns green. Then save your fingerprint."
    )
  }

  @Composable
  private fun handleCompleteFingerprintEnrollmentViaNfc(
    state: CompleteFingerprintEnrollmentViaNfcUiState,
    props: PairNewHardwareProps,
    scope: kotlinx.coroutines.CoroutineScope,
    updateState: (State) -> Unit,
  ): ScreenModel {
    LaunchedEffect("fingerprint-event") {
      eventTracker.track(action = ACTION_HW_ONBOARDING_FINGERPRINT)
    }

    // activate hardware
    return nfcSessionUIStateMachine.model(
      NfcSessionUIStateMachineProps(
        transaction = pairingTransactionProvider(
          appGlobalAuthPublicKey = state.request.appGlobalAuthPublicKey,
          onSuccess = { response ->
            handleCompleteFingerprintEnrollmentSuccess(response, state, scope, updateState)
          },
          onCancel = {
            updateState(ShowingCompleteFingerprintEnrollmentInstructionsUiState(state.request))
          },
          shouldLockHardware = false
        ),
        hardwareVerification = NotRequired,
        screenPresentationStyle = props.screenPresentationStyle,
        eventTrackerContext = NfcEventTrackerScreenIdContext.PAIR_NEW_HW_FINGERPRINT,
        onInauthenticHardware = { cause ->
          logError(throwable = cause) {
            // Inauthentic hardware should be caught on first tap. Instead of ignoring this error,
            // we'll log that it happened and reject the hardware -- even though this state
            // should be unreachable.
            "Detected inauthentic hardware in CompleteFingerprintEnrollmentViaNfcUiState," +
              "which shouldn't happen"
          }
          updateState(ShowingHelpCenter)
        }
      )
    )
  }

  private fun handleCompleteFingerprintEnrollmentSuccess(
    response: PairingTransactionResponse,
    state: CompleteFingerprintEnrollmentViaNfcUiState,
    scope: kotlinx.coroutines.CoroutineScope,
    updateState: (State) -> Unit,
  ) {
    when (response) {
      is FingerprintEnrolled -> {
        scope.launch {
          hardwareUnlockInfoService.replaceAllUnlockInfo(unlockInfoList = UnlockInfo.ONBOARDING_DEFAULT)
          eventTracker.track(action = ACTION_HW_FINGERPRINT_COMPLETE)
          state.request.onSuccess(response)
        }
      }

      FingerprintNotEnrolled -> {
        updateState(
          ShowingCompleteFingerprintEnrollmentInstructionsUiState(
            state.request,
            showingIncompleteEnrollmentError = true
          )
        )
      }

      FingerprintEnrollmentStarted -> {
        updateState(
          ShowingCompleteFingerprintEnrollmentInstructionsUiState(
            state.request,
            showingIncompleteEnrollmentError = true
          )
        )
      }
    }
  }

  @Composable
  private fun handleShowingHelpCenter(props: PairNewHardwareProps): ScreenModel {
    return helpCenterUiStateMachine.model(
      props = HelpCenterUiProps(onBack = props.onExit)
    ).copy(presentationStyle = props.screenPresentationStyle)
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
