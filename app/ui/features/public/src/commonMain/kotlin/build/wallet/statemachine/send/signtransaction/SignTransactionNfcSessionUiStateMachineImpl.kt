package build.wallet.statemachine.send.signtransaction

import androidx.compose.runtime.*
import bitkey.account.HardwareType
import bitkey.recovery.RecoveryStatusService
import bitkey.ui.verification.TxVerificationAppSegment
import build.wallet.Progress
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId.*
import build.wallet.asProgress
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.account.FullAccount
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.crypto.random.SecureRandom
import build.wallet.crypto.random.nextBytes
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SignatureVerifier
import build.wallet.encrypt.verifyEcdsaResult
import build.wallet.feature.flags.NfcSessionRetryAttemptsFeatureFlag
import build.wallet.feature.intValue
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.logDebug
import build.wallet.logging.logWarn
import build.wallet.money.display.BitcoinDisplayPreferenceRepository
import build.wallet.nfc.*
import build.wallet.nfc.NfcAvailability.Available.Disabled
import build.wallet.nfc.NfcAvailability.Available.Enabled
import build.wallet.nfc.NfcAvailability.NotAvailable
import build.wallet.nfc.NfcSession.RequirePairedHardware
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.HwDisplayPreference
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.NfcProgressCallback
import build.wallet.nfc.platform.SweepSigningContext
import build.wallet.nfc.platform.actualHardwareType
import build.wallet.nfc.platform.detectedDeviceInfo
import build.wallet.nfc.platform.toSessionFn
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.device.DevicePlatform
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.recovery.Recovery
import build.wallet.statemachine.nfc.DescriptorRepairUiProps
import build.wallet.statemachine.nfc.DescriptorRepairUiStateMachine
import build.wallet.statemachine.core.NfcErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.nfc.AndroidNfcAvailabilityUiState
import build.wallet.statemachine.nfc.AndroidNfcAvailabilityUiState.*
import build.wallet.statemachine.nfc.delayForIosNativeNfcTransition
import build.wallet.statemachine.nfc.EnableNfcInstructionsModel
import build.wallet.statemachine.nfc.HardwareConfirmationResultBodyModel
import build.wallet.statemachine.nfc.NfcHelpBodyModel
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NoNfcMessageModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.nfc.nfcThemePreference
import build.wallet.statemachine.platform.nfc.EnableNfcNavigator
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiProps
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiStateMachine
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcSessionUiState.InSessionUiState
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcSessionUiState.InSessionUiState.*
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString
import kotlin.coroutines.cancellation.CancellationException

@Suppress("LargeClass")
@BitkeyInject(ActivityScope::class)
class SignTransactionNfcSessionUiStateMachineImpl(
  private val enableNfcNavigator: EnableNfcNavigator,
  private val eventTracker: EventTracker,
  private val nfcReaderCapability: NfcReaderCapability,
  private val nfcTransactor: NfcTransactor,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val keyboxDao: KeyboxDao,
  private val signatureVerifier: SignatureVerifier,
  private val nfcSessionRetryAttemptsFeatureFlag: NfcSessionRetryAttemptsFeatureFlag,
  private val hardwareConfirmationUiStateMachine: HardwareConfirmationUiStateMachine,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val descriptorRepairUiStateMachine: DescriptorRepairUiStateMachine,
  private val recoveryStatusService: RecoveryStatusService,
  private val bitcoinDisplayPreferenceRepository: BitcoinDisplayPreferenceRepository,
) : SignTransactionNfcSessionUiStateMachine {
  private val secureRandom = SecureRandom()

  @Composable
  override fun model(props: SignTransactionNfcSessionUiProps): ScreenModel {
    val devicePlatform = remember { deviceInfoProvider.getDeviceInfo().devicePlatform }
    // Recovery sweeps can sign with a recovered account before it becomes active, so the
    // signing flow must use the caller-provided account as the hardware source of truth.
    val isHardwareFake = remember(props.account) { props.account.config.isHardwareFake }
    val hardwareType = remember(props.account, props.hardwareTypeOverride) {
      props.hardwareTypeOverride ?: props.account.config.hardwareType
    }

    var uiState by remember {
      mutableStateOf(
        determineInitialUiState(nfcReaderCapability.availability(isHardwareFake))
      )
    }

    return when (val state = uiState) {
      is InSessionUiState -> inSessionScreenModel(
        props = props,
        state = state,
        isHardwareFake = isHardwareFake,
        hardwareType = hardwareType,
        devicePlatform = devicePlatform,
        setState = { uiState = it }
      )

      is AndroidNfcAvailabilityUiState -> androidOnlyScreenModel(
        props = props,
        state = state,
        setState = { uiState = it }
      )

      is DeliveringDescriptorUiState -> {
        deliveringDescriptorScreenModel(
          props = props,
          setState = { uiState = it }
        )
      }

      else -> error("Unexpected state: $state")
    }
  }

  /**
   * Generates the screen model for in-session UI states.
   * Manages NFC transaction effects and progress tracking during transaction signing.
   */
  @Composable
  @Suppress("CyclomaticComplexMethod")
  private fun inSessionScreenModel(
    props: SignTransactionNfcSessionUiProps,
    state: InSessionUiState,
    isHardwareFake: Boolean,
    hardwareType: HardwareType,
    devicePlatform: DevicePlatform,
    setState: (Any) -> Unit,
  ): ScreenModel {
    val designSystemV2Enabled = true
    return when (state) {
      is InNfcSessionUiState -> {
        val sessionHardwareType = state.resolvedDeviceInfo?.hardwareType() ?: hardwareType

        // Track progress separately from state to avoid closure capture issues in LaunchedEffect
        var transferProgress by remember { mutableStateOf(Progress.Zero) }

        // NfcTransactionEffect stays alive for the entire InNfcSessionUiState
        NfcTransactionEffect(
          props = props,
          state = state,
          isHardwareFake = isHardwareFake,
          hardwareType = sessionHardwareType,
          setState = setState,
          onProgressUpdate = { progress -> transferProgress = progress }
        )

        val onHelpClick =
          if (designSystemV2Enabled) {
            { setState(HelpUiState(state)) }
          } else {
            null
          }
        val nfcModel = when (state.displayMode) {
          InNfcSessionUiState.DisplayMode.Searching -> {
            SignTransactionNfcBodyModel(
              onCancel = props.onBack,
              status = SignTransactionNfcBodyModel.Status.Searching,
              hardwareType = sessionHardwareType,
              showNativeSheetOnIos = props.showNativeSheetOnIos,
              onHelpClick = onHelpClick,
              eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_INITIATE, props.eventTrackerContext, eventTrackerShouldTrack = false)
            ).asFullScreen(
              devicePlatform = devicePlatform
            )
          }
          InNfcSessionUiState.DisplayMode.Signing -> {
            SignTransactionNfcBodyModel(
              onCancel = props.onBack,
              status = SignTransactionNfcBodyModel.Status.Signing,
              hardwareType = sessionHardwareType,
              showNativeSheetOnIos = props.showNativeSheetOnIos,
              // NFC_DETECTED is already tracked imperatively in onTagConnected
              eventTrackerScreenInfo = null
            ).asFullScreen(
              devicePlatform = devicePlatform
            )
          }
          InNfcSessionUiState.DisplayMode.Transferring -> {
            SignTransactionNfcBodyModel(
              onCancel = props.onBack,
              status = SignTransactionNfcBodyModel.Status.Transferring(transferProgress),
              hardwareType = sessionHardwareType,
              showNativeSheetOnIos = props.showNativeSheetOnIos,
              eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_INITIATE, props.eventTrackerContext, eventTrackerShouldTrack = false)
            ).asFullScreen(
              devicePlatform = devicePlatform
            )
          }
          InNfcSessionUiState.DisplayMode.LostConnection -> {
            SignTransactionNfcBodyModel(
              onCancel = props.onBack,
              status = SignTransactionNfcBodyModel.Status.LostConnection(transferProgress),
              hardwareType = sessionHardwareType,
              showNativeSheetOnIos = props.showNativeSheetOnIos,
              onHelpClick = onHelpClick,
              eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_INITIATE, props.eventTrackerContext, eventTrackerShouldTrack = false)
            ).asFullScreen(
              devicePlatform = devicePlatform
            )
          }
        }

        val scope = rememberStableCoroutineScope()
        val emulatedPrompt = state.emulatedPrompt
        if (emulatedPrompt != null) {
          nfcModel.copy(
            bottomSheetModel = SheetModel(
              onClosed = { props.onBack() },
              body = PromptSelectionFormBodyModel(
                details = emulatedPrompt.details,
                onApprove = {
                  scope.launch {
                    emulatedPrompt.approve.onSelect?.invoke()
                    setState(
                      AwaitingConfirmationUiState(
                        fetchResult = emulatedPrompt.approve.fetchResult,
                        resolvedDeviceInfo = state.resolvedDeviceInfo
                      )
                    )
                  }
                },
                onDeny = {
                  scope.launch {
                    emulatedPrompt.deny.onSelect?.invoke()
                    props.onBack()
                  }
                },
                onBack = { props.onBack() },
                eventTrackerContext = props.eventTrackerContext
              )
            )
          )
        } else {
          nfcModel
        }
      }

      is SuccessUiState -> {
        LaunchedEffect("sign-success") {
          logDebug { "Transaction signed successfully" }
          props.onSuccess(state.signedPsbt)
        }

        SignTransactionNfcBodyModel(
          onCancel = null,
          status = SignTransactionNfcBodyModel.Status.Success,
          hardwareType = hardwareType,
          showNativeSheetOnIos = props.showNativeSheetOnIos,
          eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_SUCCESS, props.eventTrackerContext, eventTrackerShouldTrack = false)
        ).asFullScreen(
          devicePlatform = devicePlatform
        )
      }

      is AwaitingConfirmationUiState -> {
        // Show confirmation UI for W3 two-tap flow
        hardwareConfirmationUiStateMachine.model(
          props = HardwareConfirmationUiProps(
            onBack = props.onBack,
            onConfirm = {
              // User confirmed - transition to InNfcSessionUiState with fetchResult
              // to start a new NFC session for the continuation
              setState(
                InNfcSessionUiState(
                  fetchResult = state.fetchResult,
                  resolvedDeviceInfo = state.resolvedDeviceInfo
                )
              )
            },
            content = props.confirmationContent,
            isHardwareFake = isHardwareFake
          )
        )
      }

      is HelpUiState ->
        ScreenModel(
          body = NfcHelpBodyModel(
            onBack = { setState(state.previousState) },
            devicePlatform = devicePlatform
          ),
          presentationStyle = ScreenPresentationStyle.FullScreen,
          themePreference =
            nfcThemePreference(
              devicePlatform = devicePlatform,
              followSystemOnIos = props.showNativeSheetOnIos
            )
        )

      is ErrorUiState -> {
        NfcErrorFormBodyModel(
          exception = state.exception,
          onPrimaryButtonClick = props.onBack,
          onSecondaryButtonClick = {
            when (state.exception) {
              is NfcException.InauthenticHardware -> {
                // Inauthentic hardware should be caught during pairing, fail loudly.
                error("Inauthentic hardware detected during transaction signing: ${state.exception.message}")
              }
              else -> {
                inAppBrowserNavigator.open(NfcSessionUIStateMachine.TROUBLESHOOTING_URL) {
                  // onClose callback - do nothing
                }
              }
            }
          },
          segment = TxVerificationAppSegment.Transaction,
          actionDescription = "Sign Transaction",
          eventTrackerScreenId = NfcEventTrackerScreenId.NFC_FAILURE,
          eventTrackerScreenIdContext = props.eventTrackerContext
        ).asModalScreen()
      }

      is ConfirmationPendingUiState -> {
        // W3 two-tap flow: User tapped before approving/denying on device
        val bodyModel = props.pendingBodyModel?.invoke {
          // On acknowledge, return to awaiting confirmation to retry
          setState(
            AwaitingConfirmationUiState(
              fetchResult = state.fetchResult,
              resolvedDeviceInfo = state.resolvedDeviceInfo
            )
          )
        } ?: defaultPendingBodyModel {
          setState(
            AwaitingConfirmationUiState(
              fetchResult = state.fetchResult,
              resolvedDeviceInfo = state.resolvedDeviceInfo
            )
          )
        }
        ScreenModel(body = bodyModel, presentationStyle = ScreenPresentationStyle.Modal)
      }

      is ConfirmationDeniedUiState -> {
        // W3 two-tap flow: Confirmation was not completed on device
        val bodyModel = props.deniedBodyModel?.invoke {
          // On acknowledge, return to beginning of flow (before first tap)
          setState(InNfcSessionUiState())
        } ?: defaultDeniedBodyModel {
          setState(InNfcSessionUiState())
        }
        ScreenModel(body = bodyModel, presentationStyle = ScreenPresentationStyle.Modal)
      }
    }
  }

  /**
   * Generates the screen model for the descriptor repair flow.
   * Triggered when DescriptorNotLoaded is detected — fetches the WSM signature from the
   * server, delivers the hardware descriptor via NFC, then restarts the signing flow.
   */
  @Composable
  private fun deliveringDescriptorScreenModel(
    props: SignTransactionNfcSessionUiProps,
    setState: (Any) -> Unit,
  ): ScreenModel {
    return descriptorRepairUiStateMachine.model(
        DescriptorRepairUiProps(
          fullAccount = props.account,
          presentationStyle = ScreenPresentationStyle.FullScreen,
          onRepairComplete = {
            setState(InNfcSessionUiState())
          },
          onBack = {
            props.onBack()
          },
        )
      )
  }

  /**
   * Generates the screen model for Android-only UI states.
   * Handles NFC availability issues specific to Android platform.
   */
  @Composable
  private fun androidOnlyScreenModel(
    props: SignTransactionNfcSessionUiProps,
    state: AndroidNfcAvailabilityUiState,
    setState: (Any) -> Unit,
  ): ScreenModel {
    return when (state) {
      is NoNFCMessage ->
        NoNfcMessageModel(onBack = props.onBack)
          .asModalScreen()

      is EnableNFCInstructions -> {
        EnableNfcInstructionsModel(
          onBack = props.onBack,
          onEnableClick = { setState(NavigateToEnableNFC) }
        ).asModalScreen()
      }

      is NavigateToEnableNFC -> {
        enableNfcNavigator.navigateToEnableNfc {
          setState(InNfcSessionUiState())
        }
        NoNfcMessageModel(onBack = props.onBack)
          .asModalScreen()
      }
    }
  }

  /**
   * Determines the initial UI state based on NFC availability.
   */
  private fun determineInitialUiState(availability: NfcAvailability): Any {
    return when (availability) {
      NotAvailable -> NoNFCMessage
      Disabled -> EnableNFCInstructions
      Enabled -> InNfcSessionUiState()
    }
  }

  /**
   * Handles NFC transaction failures and updates the UI state accordingly.
   */
  private fun handleNfcTransactionFailure(
    error: NfcException,
    continuation: (suspend (NfcSession, NfcCommands) -> HardwareInteraction<Psbt>)?,
    resolvedDeviceInfo: FirmwareDeviceInfo?,
    props: SignTransactionNfcSessionUiProps,
    setState: (Any) -> Unit,
  ) {
    when (error) {
      is NfcException.IOSOnly.UserCancellation -> props.onBack()
      is NfcException.DescriptorNotLoaded -> {
        // Hardware wallet descriptor is missing — trigger delivery flow before retrying
        setState(DeliveringDescriptorUiState)
      }
      is NfcException.UserDenied,
      is NfcException.ConfirmationNotCompleted -> handleUserDeniedOrConfirmationPending(
        error = error,
        continuation = continuation,
        resolvedDeviceInfo = resolvedDeviceInfo,
        props = props,
        setState = setState,
        isDenied = true
      )
      is NfcException.ConfirmationPending -> handleUserDeniedOrConfirmationPending(
        error = error,
        continuation = continuation,
        resolvedDeviceInfo = resolvedDeviceInfo,
        props = props,
        setState = setState,
        isDenied = false
      )
      else -> handleGenericError(error, props, setState)
    }
  }

  private fun handleUserDeniedOrConfirmationPending(
    error: NfcException,
    continuation: (suspend (NfcSession, NfcCommands) -> HardwareInteraction<Psbt>)?,
    resolvedDeviceInfo: FirmwareDeviceInfo?,
    props: SignTransactionNfcSessionUiProps,
    setState: (Any) -> Unit,
    isDenied: Boolean,
  ) {
    if (continuation != null) {
      val state = if (isDenied) {
        ConfirmationDeniedUiState(
          fetchResult = continuation,
          resolvedDeviceInfo = resolvedDeviceInfo
        )
      } else {
        ConfirmationPendingUiState(
          fetchResult = continuation,
          resolvedDeviceInfo = resolvedDeviceInfo
        )
      }
      setState(state)
    } else {
      handleGenericError(error, props, setState)
    }
  }

  private fun handleGenericError(
    error: NfcException,
    props: SignTransactionNfcSessionUiProps,
    setState: (Any) -> Unit,
  ) {
    val handled = props.onError(error)
    if (!handled) {
      setState(ErrorUiState(error))
    }
  }

  /**
   * Handles successful NFC transaction results and updates the UI state accordingly.
   */
  private fun handleNfcTransactionSuccess(
    result: SignTransactionResult,
    setState: (Any) -> Unit,
  ) {
    when (result) {
      is SignTransactionResult.Completed -> setState(SuccessUiState(result.signedPsbt))
      is SignTransactionResult.RequiresConfirmation -> {
        setState(
          AwaitingConfirmationUiState(
            fetchResult = result.fetchResult,
            resolvedDeviceInfo = result.resolvedDeviceInfo
          )
        )
      }
      is SignTransactionResult.RequiresEmulatedPrompt -> {
        setState(InNfcSessionUiState(emulatedPrompt = result.emulatedPrompt))
      }
    }
  }

  /**
   * Single NFC transaction effect that handles both initial transactions and continuations.
   *
   * @param state The active NFC session state. If [InNfcSessionUiState.fetchResult] is set,
   * this is a continuation from a two-tap flow and will call fetchResult to fetch the signed PSBT.
   * Otherwise, starts a fresh transaction signing flow.
   */
  @Composable
  private fun NfcTransactionEffect(
    props: SignTransactionNfcSessionUiProps,
    state: InNfcSessionUiState,
    isHardwareFake: Boolean,
    hardwareType: HardwareType,
    setState: (Any) -> Unit,
    onProgressUpdate: (Progress) -> Unit,
  ) {
    val continuation = state.fetchResult
    // Include whether this is a continuation in the key so a fresh NFC session starts
    val effectKey = "sign-transaction-${continuation != null}"

    LaunchedEffect(effectKey) {
      delayForIosNativeNfcTransition(
        devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform
      )
      // Resolve the hardware auth public key for pairing verification.
      // When useRecoveryHwAuthKey is set (recovery sweep flows), use the key from
      // the in-progress recovery if available, matching NfcSessionUIStateMachine behavior.
      val hwPubKey = if (props.useRecoveryHwAuthKey) {
        when (val recoveryStatus = recoveryStatusService.status.first()) {
          is Recovery.StillRecovering -> recoveryStatus.hardwareAuthKey.pubKey
          else -> {
            logWarn {
              "useRecoveryHwAuthKey=true but recovery is not StillRecovering, falling back to active keybox"
            }
            keyboxDao.activeKeybox().first().value?.activeHwKeyBundle?.authKey?.pubKey
          }
        }
      } else {
        keyboxDao.activeKeybox().first().value?.activeHwKeyBundle?.authKey?.pubKey
      }
      // Sign-transaction does not handle SessionCanceled explicitly; platform
      // cancellation surfaces as a Failed terminal event.
      nfcTransactor
        .transactEvents(
          parameters =
            NfcSession.Parameters(
              isHardwareFake = isHardwareFake,
              hardwareType = hardwareType,
              resolvedDeviceInfoOverride = state.resolvedDeviceInfo,
              needsAuthentication = true,
              shouldLock = true,
              skipFirmwareTelemetry = props.skipFirmwareTelemetry,
              nfcFlowName = if (continuation != null) "sign-transaction-confirmation" else "sign-transaction",
              requirePairedHardware = if (props.skipPairingCheck) {
                logWarn { "skipPairingCheck=true, using NotRequired for pairing" }
                RequirePairedHardware.NotRequired
              } else {
                hwPubKey?.let {
                  RequirePairedHardware.Required(
                    challenge = secureRandom.nextBytes(32).toByteString(),
                    checkHardwareIsPaired = { signature, challengeString ->
                      val verification = signatureVerifier.verifyEcdsaResult(
                        message = challengeString,
                        signature = signature,
                        publicKey = hwPubKey
                      )
                      verification.get() == true
                    }
                  )
                } ?: RequirePairedHardware.NotRequired
              },
              asyncNfcSigning = false,
              maxNfcRetryAttempts = nfcSessionRetryAttemptsFeatureFlag.intValue()
            ),
          transaction = { session, commands ->
            val onProgress: (Float) -> Unit = { progressFloat ->
              // First progress callback transitions from Signing (indeterminate)
              // to Transferring (determinate progress bar). Only W3 fires this.
              // Guard to avoid redundant setState on subsequent callbacks.
              if (state.displayMode != InNfcSessionUiState.DisplayMode.Transferring) {
                setState(state.copy(displayMode = InNfcSessionUiState.DisplayMode.Transferring))
              }
              session.message = "${(progressFloat * 100).toInt()}%"
              onProgressUpdate(
                progressFloat.asProgress().getOrElse {
                  if (progressFloat <= 0f) Progress.Zero else Progress.Full
                }
              )
            }
            if (continuation != null) {
              // Continuation from two-tap flow: fetch the signed PSBT
              // (for streaming signing, this may also show progress during
              // per-input signature retrieval)
              signTransactionContinuation(
                session = session,
                commands = commands,
                fetchResult = continuation,
                onProgress = onProgress
              )
            } else {
              // Fresh start: run full signTransaction
              signTransaction(
                session = session,
                commands = commands,
                props = props,
                onProgress = onProgress
              )
            }
          },
          onEvent = { event ->
            // Per-event side effects (analytics, NfcSession mutations, terminal
            // handoff) run upstream of conflation. Sign-transaction doesn't wire
            // SessionCanceled; cancellation surfaces as a Failed event.
            when (event) {
              is NfcTransactionEvent.TagConnected -> {
                eventTracker.track(EventTrackerScreenInfo(NFC_DETECTED, props.eventTrackerContext))
                event.session?.message = "This can take up to 1 minute…"
              }
              is NfcTransactionEvent.Succeeded<*> -> {
                @Suppress("UNCHECKED_CAST")
                handleNfcTransactionSuccess(event.result as SignTransactionResult, setState)
              }
              is NfcTransactionEvent.Failed ->
                handleNfcTransactionFailure(
                  error = event.error,
                  continuation = continuation,
                  resolvedDeviceInfo = state.resolvedDeviceInfo,
                  props = props,
                  setState = setState
                )
              NfcTransactionEvent.TagDisconnected,
              NfcTransactionEvent.SessionCanceled,
              -> Unit
            }
          }
        ).collect { event ->
          // UI state mutation — Flow is already conflated by transactEvents.
          when (event) {
            is NfcTransactionEvent.TagConnected -> {
              // Start in Signing (indeterminate progress) — for W1 this is the
              // final visual state before success. For W3, the first progress
              // callback will transition to Transferring (determinate progress).
              setState(state.copy(displayMode = InNfcSessionUiState.DisplayMode.Signing))
            }
            NfcTransactionEvent.TagDisconnected ->
              setState(state.copy(displayMode = InNfcSessionUiState.DisplayMode.LostConnection))
            NfcTransactionEvent.SessionCanceled,
            is NfcTransactionEvent.Succeeded<*>,
            is NfcTransactionEvent.Failed,
            -> Unit
          }
        }
    }
  }

  /**
   * Performs the full transaction signing flow.
   * Response-based routing via HardwareInteraction handles W1 vs W3 automatically.
   */
  @Throws(NfcException::class, CancellationException::class)
  private suspend fun signTransaction(
    session: NfcSession,
    commands: NfcCommands,
    props: SignTransactionNfcSessionUiProps,
    onProgress: (Float) -> Unit,
  ): SignTransactionResult {
    // Use the provided spending keyset, or fetch the active one from the account
    val spendingKeyset = props.spendingKeyset ?: props.account.keybox.activeSpendingKeyset

    // Read display preferences to send to hardware for on-device amount formatting.
    val displayPreference = HwDisplayPreference(
      bitcoinDisplayUnit = bitcoinDisplayPreferenceRepository.bitcoinDisplayUnit.value
    )

    // When a sweep context is provided and the tapped hardware resolves to W3,
    // route to the dedicated W3 sweep command so firmware uses the OLD account's
    // app/server xpubs and derives HW to the old account index. On W1 the PSBT
    // itself carries derivation paths, so use regular signTransaction even if a
    // sweep context was provided speculatively.
    val w3SweepContext = w3SweepContextForSigning(
      session = session,
      commands = commands,
      props = props
    )
    val interaction = if (w3SweepContext != null) {
      commands.sweepTransaction(
        session = session,
        psbt = props.psbt,
        spendingKeyset = spendingKeyset,
        sweepContext = w3SweepContext,
        displayPreference = displayPreference
      )
    } else {
      commands.signTransaction(
        session = session,
        psbt = props.psbt,
        spendingKeyset = spendingKeyset,
        displayPreference = displayPreference,
        allowUnfinalized = props.allowUnfinalized
      )
    }

    return when (interaction) {
      is HardwareInteraction.Completed -> {
        // W1 path: immediate completion
        SignTransactionResult.Completed(interaction.result)
      }

      is HardwareInteraction.RequiresTransfer -> {
        // W3 path: chunked transfer required
        val nextInteraction = interaction.transferAndFetch(
          session,
          commands,
          NfcProgressCallback { onProgress(it) }
        )
        // After transfer, should be RequiresConfirmation or ConfirmWithEmulatedPrompt
        when (nextInteraction) {
          is HardwareInteraction.RequiresConfirmation -> {
            SignTransactionResult.RequiresConfirmation(
              fetchResult = nextInteraction.toSessionFn(),
              resolvedDeviceInfo = commands.detectedDeviceInfo(session)
            )
          }
          is HardwareInteraction.ConfirmWithEmulatedPrompt -> {
            SignTransactionResult.RequiresEmulatedPrompt(nextInteraction)
          }
          is HardwareInteraction.Completed -> {
            // Unexpected but handle it
            SignTransactionResult.Completed(nextInteraction.result)
          }
          is HardwareInteraction.RequiresTransfer -> {
            throw NfcException.CommandError("Unexpected nested RequiresTransfer")
          }
        }
      }

      is HardwareInteraction.RequiresConfirmation -> {
        // Direct confirmation (shouldn't happen for signTransaction but handle it)
        SignTransactionResult.RequiresConfirmation(
          fetchResult = interaction.toSessionFn(),
          resolvedDeviceInfo = commands.detectedDeviceInfo(session)
        )
      }

      is HardwareInteraction.ConfirmWithEmulatedPrompt -> {
        // Fake hardware emulated prompt
        SignTransactionResult.RequiresEmulatedPrompt(interaction)
      }
    }
  }

  private suspend fun w3SweepContextForSigning(
    session: NfcSession,
    commands: NfcCommands,
    props: SignTransactionNfcSessionUiProps,
  ): SweepSigningContext? {
    val sweepContext = props.sweepSigningContext
    if (props.requiredHardwareType == null && sweepContext == null) {
      return null
    }

    val actualHardwareType = commands.actualHardwareType(session)
    verifyRequiredHardwareType(
      requiredHardwareType = props.requiredHardwareType,
      actualHardwareType = actualHardwareType
    )
    return sweepContext.takeIf { actualHardwareType == HardwareType.W3 }
  }

  private fun verifyRequiredHardwareType(
    requiredHardwareType: HardwareType?,
    actualHardwareType: HardwareType,
  ) {
    if (requiredHardwareType != null && actualHardwareType != requiredHardwareType) {
      throw NfcException.WrongHardwareType(
        expected = requiredHardwareType,
        actual = actualHardwareType
      )
    }
  }

  /**
   * Continuation transaction for two-tap flow: calls fetchResult to fetch the signed PSBT.
   *
   * For streaming signing, the second tap may return [HardwareInteraction.RequiresTransfer]
   * which triggers per-input signature retrieval in the same NFC session, with progress
   * reported through [onProgress] to drive the progress bar UI.
   */
  @Throws(NfcException::class, CancellationException::class)
  private suspend fun signTransactionContinuation(
    session: NfcSession,
    commands: NfcCommands,
    fetchResult: suspend (
      NfcSession,
      NfcCommands,
    ) -> HardwareInteraction<Psbt>,
    onProgress: (Float) -> Unit,
  ): SignTransactionResult {
    val interaction = fetchResult(session, commands)

    return when (interaction) {
      is HardwareInteraction.Completed -> {
        SignTransactionResult.Completed(interaction.result)
      }
      is HardwareInteraction.RequiresTransfer -> {
        // Streaming signing: the second tap returned RequiresTransfer to retrieve
        // per-input signatures. Execute in the same NFC session with progress.
        val nextInteraction = interaction.transferAndFetch(
          session,
          commands,
          NfcProgressCallback { progress -> onProgress(progress) }
        )
        when (nextInteraction) {
          is HardwareInteraction.Completed -> {
            SignTransactionResult.Completed(nextInteraction.result)
          }
          else -> {
            throw NfcException.CommandError(
              "Unexpected interaction after streaming signature retrieval: ${nextInteraction::class.simpleName}"
            )
          }
        }
      }
      else -> {
        throw NfcException.CommandError("Unexpected interaction type in continuation: ${interaction::class.simpleName}")
      }
    }
  }

  private fun SignTransactionNfcBodyModel.asFullScreen(
    devicePlatform: DevicePlatform,
  ) =
    ScreenModel(
      body = this,
      presentationStyle = ScreenPresentationStyle.FullScreen,
      themePreference = signTransactionNfcThemePreference(devicePlatform)
    )

  private fun SignTransactionNfcBodyModel.signTransactionNfcThemePreference(
    devicePlatform: DevicePlatform,
  ): ThemePreference =
    nfcThemePreference(
      devicePlatform = devicePlatform,
      followSystemOnIos = showNativeSheetOnIos
    )

  private fun defaultPendingBodyModel(onAcknowledge: () -> Unit) =
    HardwareConfirmationResultBodyModel(
      headline = "Review transaction on Bitkey",
      subline = "Before sending, use your Bitkey device to review the transaction details.",
      buttonText = "Got it",
      onAcknowledge = onAcknowledge,
      eventTrackerScreenId = NfcEventTrackerScreenId.NFC_CONFIRMATION_PENDING
    )

  private fun defaultDeniedBodyModel(onAcknowledge: () -> Unit) =
    HardwareConfirmationResultBodyModel(
      headline = "The transaction was not confirmed on your Bitkey",
      subline = "",
      buttonText = "OK",
      onAcknowledge = onAcknowledge,
      eventTrackerScreenId = NfcEventTrackerScreenId.NFC_CONFIRMATION_DENIED
    )
}

/**
 * Internal states for managing the transaction signing NFC session.
 * Includes both in-session states and Android NFC availability states.
 */
private sealed interface SignTransactionNfcSessionUiState {
  /**
   * States that occur during an active NFC session.
   */
  sealed interface InSessionUiState : SignTransactionNfcSessionUiState {
    /**
     * Active NFC session in progress.
     */
    data class InNfcSessionUiState(
      val displayMode: DisplayMode = DisplayMode.Searching,
      val fetchResult: (
        suspend (
          NfcSession,
          NfcCommands,
        ) -> HardwareInteraction<Psbt>
      )? = null,
      val resolvedDeviceInfo: FirmwareDeviceInfo? = null,
      val emulatedPrompt: HardwareInteraction.ConfirmWithEmulatedPrompt<Psbt>? = null,
    ) : InSessionUiState {
      enum class DisplayMode {
        /** Searching for NFC device */
        Searching,

        /** Connected and signing (shown with indeterminate progress for W1) */
        Signing,

        /** Transferring PSBT data with determinate progress (W3 chunked transfer) */
        Transferring,

        /** Lost connection during transfer */
        LostConnection,
      }
    }

    /**
     * Waiting for user to confirm transaction on device (W3 two-tap flow).
     */
    data class AwaitingConfirmationUiState(
      val fetchResult: suspend (
        NfcSession,
        NfcCommands,
      ) -> HardwareInteraction<Psbt>,
      val resolvedDeviceInfo: FirmwareDeviceInfo? = null,
    ) : InSessionUiState

    data class HelpUiState(
      val previousState: InNfcSessionUiState,
    ) : InSessionUiState

    /**
     * Transaction signed successfully.
     */
    data class SuccessUiState(
      val signedPsbt: Psbt,
    ) : InSessionUiState

    /**
     * Error occurred during signing. Shows NFC-specific error UI.
     */
    data class ErrorUiState(
      val exception: NfcException,
    ) : InSessionUiState

    /**
     * W3 two-tap flow: User tapped before approving/denying on device.
     * Shows prompt to make decision on device.
     */
    data class ConfirmationPendingUiState(
      val fetchResult: suspend (
        NfcSession,
        NfcCommands,
      ) -> HardwareInteraction<Psbt>,
      val resolvedDeviceInfo: FirmwareDeviceInfo? = null,
    ) : InSessionUiState

    /**
     * W3 two-tap flow: User explicitly denied on device.
     * Shows acknowledgment screen before returning to confirmation flow.
     */
    data class ConfirmationDeniedUiState(
      val fetchResult: suspend (
        NfcSession,
        NfcCommands,
      ) -> HardwareInteraction<Psbt>,
      val resolvedDeviceInfo: FirmwareDeviceInfo? = null,
    ) : InSessionUiState
  }
}

/**
 * Hardware wallet descriptor is missing. Running delivery flow to load it
 * before retrying the transaction signing.
 */
private data object DeliveringDescriptorUiState

/**
 * Result of a transaction signing NFC transaction.
 */
private sealed interface SignTransactionResult {
  /**
   * Signing completed successfully with the signed PSBT.
   */
  data class Completed(
    val signedPsbt: Psbt,
  ) : SignTransactionResult

  /**
   * Requires user confirmation on device before continuing (W3 two-tap flow).
   */
  data class RequiresConfirmation(
    val fetchResult: suspend (
      NfcSession,
      NfcCommands,
    ) -> HardwareInteraction<Psbt>,
    val resolvedDeviceInfo: FirmwareDeviceInfo? = null,
  ) : SignTransactionResult

  /**
   * Fake hardware requires emulated prompt selection (approve/deny).
   */
  data class RequiresEmulatedPrompt(
    val emulatedPrompt: HardwareInteraction.ConfirmWithEmulatedPrompt<Psbt>,
  ) : SignTransactionResult
}
