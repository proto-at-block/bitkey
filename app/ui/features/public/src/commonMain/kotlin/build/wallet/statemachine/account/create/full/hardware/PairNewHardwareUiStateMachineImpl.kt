package build.wallet.statemachine.account.create.full.hardware

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import bitkey.account.AccountConfigService
import bitkey.account.DefaultAccountConfig
import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
import bitkey.firmware.HardwareUnlockInfoService
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_FINGERPRINT_ENROLLMENT_HELP
import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_HW_FINGERPRINT_COMPLETE
import build.wallet.analytics.v1.Action.ACTION_HW_ONBOARDING_FINGERPRINT
import build.wallet.analytics.v1.Action.ACTION_HW_ONBOARDING_OPEN
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.isEnabled
import build.wallet.feature.flags.W3OnboardingFeatureFlag
import build.wallet.firmware.UnlockInfo
import build.wallet.logging.*
import build.wallet.nfc.NfcException
import build.wallet.nfc.transaction.PairingTransactionProvider
import build.wallet.nfc.transaction.PairingTransactionResponse
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrolled
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintEnrollmentStarted
import build.wallet.nfc.transaction.PairingTransactionResponse.FingerprintNotEnrolled
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.statemachine.account.create.full.OnboardingAppSegment
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.CompleteFingerprintEnrollmentViaNfcUiState
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.ShowingActivationInstructionsUiState
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.ShowingActivationInstructionsV2UiState
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.ShowingCompleteFingerprintEnrollmentInstructionsUiState
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.ShowingHelpCenter
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.ShowingStartFingerprintEnrollmentInstructionsUiState
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.ShowingWrongHardwareError
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachineImpl.State.StartFingerprintEnrollmentViaNfcUiState
import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetDragHandleTreatment.OVERLAY
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationHelpBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationHelpContent.Companion.TapBitkey
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
  private val appSessionManager: AppSessionManager,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val hardwareUnlockInfoService: HardwareUnlockInfoService,
  private val w3OnboardingFeatureFlag: W3OnboardingFeatureFlag,
  private val accountConfigService: AccountConfigService,
) : PairNewHardwareUiStateMachine {
  private val seenTapBitkeyIntroSessionKeys = mutableSetOf<String>()

  @Composable
  override fun model(props: PairNewHardwareProps): ScreenModel {
    val scope = rememberStableCoroutineScope()
    val isDesignSystemV2Enabled = true
    val devicePlatform = remember { deviceInfoProvider.getDeviceInfo().devicePlatform }
    // W3Upgrade context always forces W3 onboarding, regardless of feature flag.
    val isW3Flow = props.pairingContext is PairingContext.W3Upgrade || w3OnboardingFeatureFlag.isEnabled()
    // Only override hardware type for fake hardware — real hardware auto-detects from firmware.
    val isHardwareFake = remember {
      when (val config = accountConfigService.activeOrDefaultConfig().value) {
        is FullAccountConfig -> config.isHardwareFake
        is DefaultAccountConfig -> config.isHardwareFake
        else -> false
      }
    }
    val hardwareTypeOverride = remember(props.pairingContext, isHardwareFake) {
      if (!isHardwareFake) {
        // Real hardware auto-detects from firmware — no override needed.
        null
      } else {
        when (props.pairingContext) {
          // W3 upgrade always forces W3 fake hardware.
          is PairingContext.W3Upgrade -> HardwareType.W3
          // Lost-hardware recovery: the replacement device type comes from the default config
          // (not the active account, which still holds the old hardware type).
          is PairingContext.LostHardware ->
            accountConfigService.defaultConfig().value.hardwareType
          else -> null
        }
      }
    }
    var state: State by remember {
      val initialState = if (isW3Flow) {
        ShowingActivationInstructionsV2UiState()
      } else {
        ShowingActivationInstructionsUiState()
      }
      mutableStateOf(initialState)
    }
    val tapBitkeyIntroSessionKey = tapBitkeyIntroSessionKey(props.pairingContext)
    var hasSeenTapBitkeyIntroSheet by remember(tapBitkeyIntroSessionKey) {
      mutableStateOf(hasSeenTapBitkeyIntroSheet(tapBitkeyIntroSessionKey))
    }
    val shouldShowTapBitkeyIntroSheet =
      isW3Flow
    val pairNewHardwareBodyModelPresentationStyle = determinePresentationStyle(props.screenPresentationStyle)

    return when (val s = state) {
      is ShowingActivationInstructionsV2UiState ->
        handleActivationInstructionsV2(
          s,
          props,
          pairNewHardwareBodyModelPresentationStyle,
          devicePlatform,
          shouldShowTapBitkeyIntroSheet = shouldShowTapBitkeyIntroSheet,
          hasSeenTapBitkeyIntroSheet = hasSeenTapBitkeyIntroSheet,
          markTapBitkeyIntroSheetSeen = {
            markTapBitkeyIntroSheetSeen(tapBitkeyIntroSessionKey)
            hasSeenTapBitkeyIntroSheet = true
          }
        ) { state = it }

      is ShowingActivationInstructionsUiState ->
        handleActivationInstructions(s, props, pairNewHardwareBodyModelPresentationStyle) { state = it }

      is ShowingStartFingerprintEnrollmentInstructionsUiState ->
        handleStartFingerprintEnrollmentInstructions(
          s,
          props,
          pairNewHardwareBodyModelPresentationStyle
        ) {
          state = it
        }

      is StartFingerprintEnrollmentViaNfcUiState ->
        handleStartFingerprintEnrollmentViaNfc(s, props, scope, hardwareTypeOverride) {
          state = it
        }

      is ShowingCompleteFingerprintEnrollmentInstructionsUiState ->
        handleCompleteFingerprintEnrollmentInstructions(
          s,
          props,
          pairNewHardwareBodyModelPresentationStyle,
          devicePlatform,
          isHardwareFake,
          isDesignSystemV2Enabled
        ) {
          state = it
        }

      is CompleteFingerprintEnrollmentViaNfcUiState ->
        handleCompleteFingerprintEnrollmentViaNfc(s, props, scope, s.hardwareType) { state = it }

      is ShowingHelpCenter ->
        handleShowingHelpCenter(props)

      is ShowingWrongHardwareError ->
        handleShowingWrongHardwareError(s) { state = it }
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
  private fun handleActivationInstructionsV2(
    state: ShowingActivationInstructionsV2UiState,
    props: PairNewHardwareProps,
    presentationStyle: ScreenPresentationStyle,
    devicePlatform: build.wallet.platform.device.DevicePlatform,
    shouldShowTapBitkeyIntroSheet: Boolean,
    hasSeenTapBitkeyIntroSheet: Boolean,
    markTapBitkeyIntroSheetSeen: () -> Unit,
    updateState: (State) -> Unit,
  ): ScreenModel {
    when (state.helpScreen) {
      ActivationInstructionsV2HelpScreen.FingerprintEnrollment -> {
        return ScreenModel(
          body = FingerprintEnrollmentHelpBodyModel(
            onBack = { updateState(state.copy(helpScreen = null)) },
            eventTrackerContext = props.eventTrackerContext,
            devicePlatform = devicePlatform
          ),
          presentationStyle = presentationStyle,
          themePreference = ThemePreference.Manual(Theme.DARK)
        )
      }

      null -> Unit
    }

    val dismissTapBitkeyIntroSheet = {
      markTapBitkeyIntroSheetSeen()
    }

    return ScreenModel(
      body = ActivationInstructionsV2BodyModel(
        onContinue = when (props.request) {
          is PairNewHardwareProps.Request.Ready -> {
            {
              updateState(
                StartFingerprintEnrollmentViaNfcUiState(
                  request = props.request,
                  returnToW3ActivationOnCancel = true
                )
              )
            }
          }
          else -> null
        },
        onBack = props.onExit,
        onHelpClick = {
          updateState(state.copy(helpScreen = ActivationInstructionsV2HelpScreen.FingerprintEnrollment))
        },
        isNavigatingBack = state.isNavigatingBack,
        eventTrackerContext = props.eventTrackerContext
      ),
      presentationStyle = presentationStyle,
      themePreference = ThemePreference.Manual(Theme.DARK),
      bottomSheetModel =
        if (shouldShowTapBitkeyIntroSheet && !hasSeenTapBitkeyIntroSheet) {
          SheetModel(
            dragHandleTreatment = OVERLAY,
            onClosed = dismissTapBitkeyIntroSheet,
            body = TapBitkeyIntroSheetBodyModel(
              onDismiss = dismissTapBitkeyIntroSheet,
              onLearnMore = {
                markTapBitkeyIntroSheetSeen()
                updateState(state.copy(helpScreen = ActivationInstructionsV2HelpScreen.FingerprintEnrollment))
              },
              onHasNoScreen =
                if (props.pairingContext is PairingContext.Onboarding) {
                  {
                    markTapBitkeyIntroSheetSeen()
                    updateState(ShowingActivationInstructionsUiState())
                  }
                } else {
                  null
                },
              devicePlatform = devicePlatform
            )
          )
        } else {
          null
        }
    )
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
    hardwareTypeOverride: HardwareType?,
    updateState: (State) -> Unit,
  ): ScreenModel {
    val isW3UpgradeFlow = props.pairingContext is PairingContext.W3Upgrade
    val isLostHardwareRecovery = props.pairingContext == PairingContext.LostHardware

    LaunchedEffect("pairing-event") {
      eventTracker.track(action = ACTION_HW_ONBOARDING_OPEN)
    }

    return nfcSessionUIStateMachine.model(
      NfcSessionUIStateMachineProps(
        transaction = pairingTransactionProvider(
          onCancel = {
            if (state.returnToW3ActivationOnCancel) {
              updateState(
                ShowingActivationInstructionsV2UiState(
                  isNavigatingBack = true
                )
              )
            } else {
              updateState(
                ShowingStartFingerprintEnrollmentInstructionsUiState(
                  state.request,
                  isNavigatingBack = true
                )
              )
            }
          },
          onSuccess = { response ->
            handleStartFingerprintEnrollmentSuccess(
              response = response,
              state = state,
              scope = scope,
              pairingContext = props.pairingContext,
              segment = props.segment,
              updateState = updateState
            )
          },
          appGlobalAuthPublicKey = state.request.appGlobalAuthPublicKey,
          shouldLockHardware = isLostHardwareRecovery,
          expectedHardwareType = props.pairingContext.expectedHardwareType(),
          skipAppInstallationUpdate = isW3UpgradeFlow
        ),
        screenPresentationStyle = props.screenPresentationStyle,
        segment = props.segment,
        hardwareVerification = NotRequired,
        actionDescription = "Pairing new hardware",
        eventTrackerContext = NfcEventTrackerScreenIdContext.PAIR_NEW_HW_ACTIVATION,
        onInauthenticHardware = { updateState(ShowingHelpCenter) },
        hardwareTypeOverride = hardwareTypeOverride,
        onError = wrongHardwareErrorHandler(
          retryState = state,
          segment = props.segment,
          updateState = updateState
        ),
        skipFirmwareTelemetry = isW3UpgradeFlow
      )
    )
  }

  private fun handleStartFingerprintEnrollmentSuccess(
    response: PairingTransactionResponse,
    state: StartFingerprintEnrollmentViaNfcUiState,
    scope: kotlinx.coroutines.CoroutineScope,
    pairingContext: PairingContext,
    segment: AppSegment?,
    updateState: (State) -> Unit,
  ) {
    // During W3 upgrade, enforce that the correct hardware type is tapped.
    if (pairingContext is PairingContext.W3Upgrade && response.hardwareType != HardwareType.W3) {
      updateState(
        ShowingWrongHardwareError(
          retryState = state,
          segment = segment,
          cause = IllegalStateException("Wrong hardware type tapped during W3 upgrade")
        )
      )
      return
    }

    // Outside of W3 upgrade, use the hardware type detected from the device firmware, silently
    // switching from the expected type if necessary. This allows users to tap either W1 or W3
    // devices and the app will automatically use the correct flow in those contexts.
    when (response) {
      is FingerprintEnrolled -> {
        // Fingerprint already enrolled on hardware - complete directly
        scope.launch {
          if (pairingContext !is PairingContext.W3Upgrade) {
            hardwareUnlockInfoService.replaceAllUnlockInfo(unlockInfoList = UnlockInfo.ONBOARDING_DEFAULT)
          }
          eventTracker.track(action = ACTION_HW_FINGERPRINT_COMPLETE)
          state.request.onSuccess(response)
        }
      }
      is FingerprintEnrollmentStarted -> {
        // Show waiting screen - W3 shows "Finished on your device?", legacy shows fingerprint instructions
        updateState(ShowingCompleteFingerprintEnrollmentInstructionsUiState(state.request, hardwareType = response.hardwareType))
      }
      is FingerprintNotEnrolled -> {
        // Fingerprint enrollment was started but incomplete (e.g., user cancelled previous attempt)
        updateState(ShowingCompleteFingerprintEnrollmentInstructionsUiState(state.request, hardwareType = response.hardwareType))
      }
    }
  }

  @Composable
  private fun handleCompleteFingerprintEnrollmentInstructions(
    state: ShowingCompleteFingerprintEnrollmentInstructionsUiState,
    props: PairNewHardwareProps,
    presentationStyle: ScreenPresentationStyle,
    devicePlatform: build.wallet.platform.device.DevicePlatform,
    isHardwareFake: Boolean,
    isDesignSystemV2Enabled: Boolean,
    updateState: (State) -> Unit,
  ): ScreenModel {
    // W3 help sub-state - show help screen
    if (state.hardwareType == HardwareType.W3 && state.showingHelp) {
      return ScreenModel(
        body = HardwareConfirmationHelpBodyModel(
          onBack = { updateState(state.copy(showingHelp = false)) },
          content = TapBitkey,
          devicePlatform = devicePlatform,
          eventTrackerScreenIdOverride = HW_FINGERPRINT_ENROLLMENT_HELP,
          eventTrackerContext = props.eventTrackerContext,
          eventTrackerShouldTrackOverride = true
        ),
        presentationStyle = presentationStyle,
        themePreference = screenThemePreference(isDesignSystemV2Enabled)
      )
    }

    // W3 flow - show "Finished on your device?" screen
    if (state.hardwareType == HardwareType.W3) {
      return ScreenModel(
        body = CompleteTwoTapBodyModel(
          onContinue = {
            updateState(CompleteFingerprintEnrollmentViaNfcUiState(state.request, hardwareType = HardwareType.W3))
          },
          onBack = props.onExit,
          onHelpClick = { updateState(state.copy(showingHelp = true)) },
          eventTrackerContext = props.eventTrackerContext,
          isHardwareFake = isHardwareFake
        ),
        presentationStyle = presentationStyle,
        themePreference = screenThemePreference(isDesignSystemV2Enabled)
      )
    }

    // Legacy flow - show fingerprint enrollment instructions
    return HardwareFingerprintEnrollmentScreenModel(
      showingIncompleteEnrollmentError = state.showingIncompleteEnrollmentError,
      incompleteEnrollmentErrorOnPrimaryButtonClick = {
        updateState(state.copy(showingIncompleteEnrollmentError = false))
      },
      onSaveFingerprint = {
        updateState(CompleteFingerprintEnrollmentViaNfcUiState(state.request, hardwareType = state.hardwareType))
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
    hardwareTypeOverride: HardwareType?,
    updateState: (State) -> Unit,
  ): ScreenModel {
    val isW3UpgradeFlow = props.pairingContext is PairingContext.W3Upgrade
    val isLostHardwareRecovery = props.pairingContext == PairingContext.LostHardware

    LaunchedEffect("fingerprint-event") {
      eventTracker.track(action = ACTION_HW_ONBOARDING_FINGERPRINT)
    }

    // activate hardware
    return nfcSessionUIStateMachine.model(
      NfcSessionUIStateMachineProps(
        transaction = pairingTransactionProvider(
          appGlobalAuthPublicKey = state.request.appGlobalAuthPublicKey,
          onSuccess = { response ->
            handleCompleteFingerprintEnrollmentSuccess(
              response = response,
              state = state,
              scope = scope,
              pairingContext = props.pairingContext,
              segment = props.segment,
              updateState = updateState
            )
          },
          onCancel = {
            updateState(ShowingCompleteFingerprintEnrollmentInstructionsUiState(state.request, hardwareType = state.hardwareType))
          },
          shouldLockHardware = isLostHardwareRecovery,
          expectedHardwareType = props.pairingContext.expectedHardwareType(),
          skipAppInstallationUpdate = isW3UpgradeFlow
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
        },
        hardwareTypeOverride = hardwareTypeOverride,
        onError = wrongHardwareErrorHandler(
          retryState = state,
          segment = props.segment,
          updateState = updateState
        ),
        skipFirmwareTelemetry = isW3UpgradeFlow
      )
    )
  }

  private fun handleCompleteFingerprintEnrollmentSuccess(
    response: PairingTransactionResponse,
    state: CompleteFingerprintEnrollmentViaNfcUiState,
    scope: kotlinx.coroutines.CoroutineScope,
    pairingContext: PairingContext,
    segment: AppSegment?,
    updateState: (State) -> Unit,
  ) {
    // During W3 upgrade, enforce that the correct hardware type is tapped.
    if (pairingContext is PairingContext.W3Upgrade && response.hardwareType != HardwareType.W3) {
      updateState(
        ShowingWrongHardwareError(
          retryState = CompleteFingerprintEnrollmentViaNfcUiState(state.request, hardwareType = state.hardwareType),
          segment = segment,
          cause = IllegalStateException("Wrong hardware type tapped during W3 upgrade")
        )
      )
      return
    }

    when (response) {
      is FingerprintEnrolled -> {
        scope.launch {
          if (pairingContext !is PairingContext.W3Upgrade) {
            hardwareUnlockInfoService.replaceAllUnlockInfo(unlockInfoList = UnlockInfo.ONBOARDING_DEFAULT)
          }
          eventTracker.track(action = ACTION_HW_FINGERPRINT_COMPLETE)
          state.request.onSuccess(response)
        }
      }

      is FingerprintNotEnrolled -> {
        updateState(
          ShowingCompleteFingerprintEnrollmentInstructionsUiState(
            request = state.request,
            hardwareType = state.hardwareType,
            showingIncompleteEnrollmentError = true
          )
        )
      }

      is FingerprintEnrollmentStarted -> {
        updateState(
          ShowingCompleteFingerprintEnrollmentInstructionsUiState(
            request = state.request,
            hardwareType = state.hardwareType,
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

  @Composable
  private fun handleShowingWrongHardwareError(
    state: ShowingWrongHardwareError,
    updateState: (State) -> Unit,
  ): ScreenModel {
    return ScreenModel(
      body = ErrorFormBodyModel(
        title = "Wrong Bitkey tapped",
        subline = "Please tap your new Bitkey device to continue.",
        primaryButton = ButtonDataModel(
          text = "Retry",
          onClick = { updateState(state.retryState) }
        ),
        eventTrackerScreenId = WalletMigrationEventTrackerScreenId.W3_UPGRADE_WRONG_HARDWARE_ERROR,
        errorData = ErrorData(
          segment = state.segment ?: OnboardingAppSegment.FullAccount,
          actionDescription = "Pairing new hardware",
          cause = state.cause
        )
      ),
      presentationStyle = ScreenPresentationStyle.RootFullScreen,
      themePreference = ThemePreference.Manual(Theme.DARK)
    )
  }

  /**
   * Creates an error handler for NFC sessions that routes [NfcException.WrongHardwareType]
   * to [ShowingWrongHardwareError] for retry, letting other errors fall through.
   */
  private fun wrongHardwareErrorHandler(
    retryState: State,
    segment: AppSegment?,
    updateState: (State) -> Unit,
  ): (NfcException) -> Boolean =
    { exception ->
      if (exception is NfcException.WrongHardwareType) {
        updateState(
          ShowingWrongHardwareError(
            retryState = retryState,
            segment = segment,
            cause = exception
          )
        )
        true
      } else {
        false
      }
    }

  private enum class ActivationInstructionsV2HelpScreen {
    FingerprintEnrollment,
  }

  private sealed interface State {
    /**
     * W3 onboarding: Showing activation instructions V2 screen introducing hardware round trip concept.
     */
    data class ShowingActivationInstructionsV2UiState(
      val isNavigatingBack: Boolean = false,
      val helpScreen: ActivationInstructionsV2HelpScreen? = null,
    ) : State

    /**
     * Showing instructions for how to activate the new hardware (legacy W1/W1A flow).
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
      val returnToW3ActivationOnCancel: Boolean = false,
    ) : State

    /**
     * Showing instructions/waiting screen for fingerprint enrollment.
     * When [hardwareType] is [HardwareType.W3], shows "Finished on your device?" screen with help option.
     * When [hardwareType] is [HardwareType.W1], shows legacy fingerprint enrollment instructions.
     */
    data class ShowingCompleteFingerprintEnrollmentInstructionsUiState(
      val request: PairNewHardwareProps.Request.Ready,
      val hardwareType: HardwareType = HardwareType.W1,
      val showingIncompleteEnrollmentError: Boolean = false,
      val showingHelp: Boolean = false,
      val isNavigatingBack: Boolean = false,
    ) : State

    /**
     * Showing NFC screen, waiting for customer to complete instructions and tap the hardware to
     * confirm fingerprint enrollment.
     */
    data class CompleteFingerprintEnrollmentViaNfcUiState(
      val request: PairNewHardwareProps.Request.Ready,
      val hardwareType: HardwareType = HardwareType.W1,
    ) : State

    data object ShowingHelpCenter : State

    /**
     * Showing error when wrong hardware type was tapped during W3 upgrade.
     * Contains the state to return to for retry.
     */
    data class ShowingWrongHardwareError(
      val retryState: State,
      val segment: AppSegment?,
      val cause: Throwable,
    ) : State
  }

  private fun tapBitkeyIntroSessionKey(pairingContext: PairingContext): String {
    return "${appSessionManager.getSessionId()}:$pairingContext"
  }

  private fun hasSeenTapBitkeyIntroSheet(sessionKey: String): Boolean {
    return seenTapBitkeyIntroSessionKeys.contains(sessionKey)
  }

  private fun markTapBitkeyIntroSheetSeen(sessionKey: String) {
    seenTapBitkeyIntroSessionKeys += sessionKey
  }

  private fun screenThemePreference(isDesignSystemV2Enabled: Boolean): ThemePreference {
    return if (isDesignSystemV2Enabled) {
      ThemePreference.System
    } else {
      ThemePreference.Manual(Theme.DARK)
    }
  }
}
