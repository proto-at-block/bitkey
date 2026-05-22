package build.wallet.statemachine.fwup

import androidx.compose.runtime.*
import bitkey.account.AccountConfig
import bitkey.account.AccountConfigService
import bitkey.account.DefaultAccountConfig
import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.FwupMcuEventTrackerContext
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.FWUP
import build.wallet.analytics.events.screen.id.FwupEventTrackerScreenId.NFC_DEVICE_LOST_CONNECTION_FWUP
import build.wallet.analytics.events.screen.id.FwupEventTrackerScreenId.NFC_UPDATE_IN_PROGRESS_FWUP
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId.*
import build.wallet.analytics.v1.Action
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.coroutines.scopes.mapAsStateFlow
import build.wallet.crypto.random.SecureRandom
import build.wallet.crypto.random.nextBytes
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SignatureVerifier
import build.wallet.encrypt.verifyEcdsaResult
import build.wallet.feature.flags.FwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag
import build.wallet.feature.flags.FwupNfcCooldownPeriodSecondsFeatureFlag
import build.wallet.feature.flags.NfcSessionRetryAttemptsFeatureFlag
import build.wallet.feature.intValue
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.fwup.*
import build.wallet.fwup.FwupFinishResponseStatus.*
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.logging.logWarn
import build.wallet.nfc.*
import build.wallet.nfc.NfcAvailability.Available.Disabled
import build.wallet.nfc.NfcAvailability.Available.Enabled
import build.wallet.nfc.NfcAvailability.NotAvailable
import build.wallet.nfc.NfcSession.RequirePairedHardware
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.detectedDeviceInfo
import build.wallet.nfc.platform.toSessionFn
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.fwup.FwupNfcSessionUiState.AndroidOnlyUiState
import build.wallet.statemachine.fwup.FwupNfcSessionUiState.AndroidOnlyUiState.*
import build.wallet.statemachine.fwup.FwupNfcSessionUiState.InSessionUiState
import build.wallet.statemachine.fwup.FwupNfcSessionUiState.InSessionUiState.*
import build.wallet.statemachine.nfc.EnableNfcInstructionsModel
import build.wallet.statemachine.nfc.HardwareConfirmationResultBodyModel
import build.wallet.statemachine.nfc.NfcSuccessScreenDuration
import build.wallet.statemachine.nfc.NoNfcMessageModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.nfc.delayForIosNativeNfcTransition
import build.wallet.statemachine.platform.nfc.EnableNfcNavigator
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationContent
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiProps
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiStateMachine
import build.wallet.toUByteList
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Suppress("LargeClass")
@BitkeyInject(ActivityScope::class)
class FwupNfcSessionUiStateMachineImpl(
  private val enableNfcNavigator: EnableNfcNavigator,
  private val eventTracker: EventTracker,
  private val fwupProgressCalculator: FwupProgressCalculator,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val nfcReaderCapability: NfcReaderCapability,
  private val nfcTransactor: NfcTransactor,
  private val fwupDataDao: FwupDataDao,
  private val firmwareDataService: FirmwareDataService,
  private val accountConfigService: AccountConfigService,
  private val keyboxDao: KeyboxDao,
  private val signatureVerifier: SignatureVerifier,
  private val nfcSessionRetryAttemptsFeatureFlag: NfcSessionRetryAttemptsFeatureFlag,
  private val fwupNfcCooldownPeriodSecondsFeatureFlag: FwupNfcCooldownPeriodSecondsFeatureFlag,
  private val fwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag:
    FwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag,
  private val hardwareConfirmationUiStateMachine: HardwareConfirmationUiStateMachine,
) : FwupNfcSessionUiStateMachine {
  private val secureRandom = SecureRandom()
  private var fwupInProgress = false

  @Composable
  override fun model(props: FwupNfcSessionUiProps): ScreenModel {
    val scope = rememberStableCoroutineScope()

    // Get the active keybox to use its config as the source of truth for hardware type.
    // This prevents a race condition where activeOrDefaultConfig() could return the fallback
    // config (W1) before emitting the active account's W3 config.
    val activeKeybox by remember {
      keyboxDao.activeKeybox().map { it.get() }
    }.collectAsState(initial = null)

    // Fall back to activeOrDefaultConfig() if no active keybox exists (e.g., debug menu
    // before account creation). Once a keybox exists, use its config.
    val defaultConfig by remember {
      accountConfigService.activeOrDefaultConfig()
    }.collectAsState()
    val isHardwareFake = activeKeybox?.config?.isHardwareFake
      ?: extractIsHardwareFake(defaultConfig)
    // Use hardwareTypeOverride if provided (e.g., from debug menu when real hardware is enabled
    // but we need to derive hardware type from the firmware update itself).
    val hardwareType = props.hardwareTypeOverride
      ?: activeKeybox?.config?.hardwareType
      ?: extractHardwareType(defaultConfig)

    // Use explicitly selected MCU updates when provided (e.g. from debug menu),
    // otherwise fall back to all pending updates from the firmware data service.
    val mcuUpdates by if (props.selectedMcuUpdates.isNotEmpty()) {
      remember(props.selectedMcuUpdates) {
        mutableStateOf(props.selectedMcuUpdates)
      }
    } else {
      remember {
        firmwareDataService.firmwareData().mapAsStateFlow(scope) {
          extractMcuUpdates(it.firmwareUpdateState)
        }
      }.collectAsState()
    }

    var uiState by remember {
      mutableStateOf(
        determineInitialUiState(
          availability = nfcReaderCapability.availability(isHardwareFake),
          mcuUpdates = mcuUpdates,
          transactionType = props.transactionType
        )
      )
    }

    var fwupProgress by remember { mutableStateOf(0.0f) }
    val nfcCooldownDurationSeconds = fwupNfcCooldownPeriodSecondsFeatureFlag.intValue()
    val hiddenNfcScreenRevealDelayMs =
      fwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag.intValue()

    return when (val state = uiState) {
      is InSessionUiState -> inSessionScreenModel(
        props = props,
        state = state,
        isHardwareFake = isHardwareFake,
        hardwareType = hardwareType,
        fwupProgress = fwupProgress,
        nfcCooldownDurationSeconds = nfcCooldownDurationSeconds,
        hiddenNfcScreenRevealDelayMs = hiddenNfcScreenRevealDelayMs,
        setProgress = { fwupProgress = it },
        getCurrentState = { uiState },
        setState = { uiState = it }
      )

      is AndroidOnlyUiState -> androidOnlyScreenModel(
        props = props,
        state = state,
        mcuUpdates = mcuUpdates,
        transactionType = props.transactionType,
        setState = { uiState = it }
      )
    }
  }

  /**
   * Generates the screen model for in-session UI states (Searching, Updating, LostConnection, Success).
   * Manages NFC transaction effects and progress tracking during firmware updates.
   */
  @Suppress("CyclomaticComplexMethod")
  @Composable
  private fun inSessionScreenModel(
    props: FwupNfcSessionUiProps,
    state: InSessionUiState,
    isHardwareFake: Boolean,
    hardwareType: HardwareType,
    fwupProgress: Float,
    nfcCooldownDurationSeconds: Int,
    hiddenNfcScreenRevealDelayMs: Int,
    setProgress: (Float) -> Unit,
    getCurrentState: () -> FwupNfcSessionUiState,
    setState: (FwupNfcSessionUiState) -> Unit,
  ): ScreenModel {
    val designSystemV2Enabled = true
    return when (state) {
      is InNfcSessionUiState -> {
        // NfcTransactionEffect stays alive for the entire InNfcSessionUiState,
        // regardless of displayMode changes (Searching -> Updating -> LostConnection)
        NfcTransactionEffect(
          props = props,
          state = state,
          isHardwareFake = isHardwareFake,
          hardwareType = hardwareType,
          designSystemV2Enabled = designSystemV2Enabled,
          hiddenNfcScreenRevealDelayMs = hiddenNfcScreenRevealDelayMs,
          setProgress = setProgress,
          getCurrentState = getCurrentState,
          setState = setState
        )

        val nfcModel = when (state.displayMode) {
          InNfcSessionUiState.DisplayMode.BackgroundRetryStartup -> {
            ScreenModel(
              body = FwupNfcCooldownModel(
                onBack = props.onBack,
                remainingSeconds = 0,
                onContinue = null,
                isStartingSession = true
              )
            )
          }
          InNfcSessionUiState.DisplayMode.Searching -> {
            FwupNfcBodyModel(
              onCancel = props.onBack,
              status = FwupNfcBodyModel.Status.Searching(),
              showNativeSheetOnIos = props.showNativeSheetOnIos,
              eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_INITIATE, FWUP)
            ).asFwupProgressScreen(designSystemV2Enabled)
          }
          InNfcSessionUiState.DisplayMode.Updating -> {
            FwupNfcBodyModel(
              onCancel = props.onBack,
              status = FwupNfcBodyModel.Status.InProgress(
                currentMcuRole = state.currentMcu.mcuRole,
                mcuIndex = state.currentMcuIndex,
                totalMcus = state.totalMcus,
                fwupProgress = fwupProgress
              ),
              showNativeSheetOnIos = props.showNativeSheetOnIos,
              eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_UPDATE_IN_PROGRESS_FWUP)
            ).asFwupProgressScreen(designSystemV2Enabled)
          }
          InNfcSessionUiState.DisplayMode.LostConnection -> {
            FwupNfcBodyModel(
              onCancel = props.onBack,
              status = FwupNfcBodyModel.Status.LostConnection(
                currentMcuRole = state.currentMcu.mcuRole,
                mcuIndex = state.currentMcuIndex,
                totalMcus = state.totalMcus,
                fwupProgress = fwupProgress
              ),
              showNativeSheetOnIos = props.showNativeSheetOnIos,
              eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_DEVICE_LOST_CONNECTION_FWUP)
            ).asPlatformNfcScreen(
              designSystemV2Enabled = designSystemV2Enabled,
              devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform
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
                      InNfcSessionUiState(
                        mcuUpdates = state.mcuUpdates,
                        currentMcuIndex = state.currentMcuIndex,
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
                eventTrackerContext = FWUP
              )
            )
          )
        } else {
          nfcModel
        }
      }

      is SuccessUiState -> {
        LaunchedEffect("fwup-success") {
          firmwareDataService.updateFirmwareVersion(state.mcuUpdates)
          eventTracker.track(Action.ACTION_APP_FWUP_COMPLETE)
          delay(
            NfcSuccessScreenDuration(
              devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform,
              isHardwareFake = isHardwareFake
            )
          )
          props.onDone()
        }

        FwupNfcBodyModel(
          onCancel = null,
          status = FwupNfcBodyModel.Status.Success(),
          showNativeSheetOnIos = props.showNativeSheetOnIos,
          eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_SUCCESS, FWUP)
        ).asPlatformNfcScreen(
          designSystemV2Enabled = designSystemV2Enabled,
          devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform
        )
      }

      is AwaitingConfirmationUiState -> {
        // Show confirmation UI for W3 two-tap flow
        hardwareConfirmationUiStateMachine.model(
          props = HardwareConfirmationUiProps(
            onBack = props.onBack,
            content = HardwareConfirmationContent.FirmwareUpdate,
            onConfirm = {
              // User confirmed - transition to InNfcSessionUiState with fetchResult
              // to start a new NFC session for the continuation
              setState(
                InNfcSessionUiState(
                  mcuUpdates = state.mcuUpdates,
                  currentMcuIndex = state.currentMcuIndex,
                  fetchResult = state.fetchResult,
                  resolvedDeviceInfo = state.resolvedDeviceInfo
                )
              )
            },
            isHardwareFake = isHardwareFake
          )
        )
      }

      is AwaitingNextMcuStartUiState -> {
        ScreenModel(
          body = FwupNextComponentReadyModel(
            completedIndex = state.currentMcuIndex, // 1-based for display (currentMcuIndex is already +1)
            totalMcus = state.totalMcus,
            onBack = props.onBack,
            onContinue = {
              // Start NFC session for the next MCU
              setState(
                InNfcSessionUiState(
                  mcuUpdates = state.mcuUpdates,
                  currentMcuIndex = state.currentMcuIndex
                )
              )
            }
          )
        )
      }

      is NfcCooldownUiState -> {
        var remainingSeconds by remember(state.currentMcuIndex, state.mcuUpdates) {
          mutableIntStateOf(nfcCooldownDurationSeconds)
        }
        LaunchedEffect("nfc-cooldown-retry-${state.currentMcuIndex}") {
          for (seconds in nfcCooldownDurationSeconds downTo 1) {
            remainingSeconds = seconds
            delay(1.seconds)
          }
          remainingSeconds = 0
        }
        ScreenModel(
          body = FwupNfcCooldownModel(
            onBack = props.onBack,
            remainingSeconds = remainingSeconds,
            onContinue =
              if (remainingSeconds == 0) {
                {
                  setState(
                    InNfcSessionUiState(
                      mcuUpdates = state.mcuUpdates,
                      currentMcuIndex = state.currentMcuIndex,
                      displayMode = InNfcSessionUiState.DisplayMode.BackgroundRetryStartup
                    )
                  )
                }
              } else {
                null
              }
          )
        )
      }

      is ConfirmationPendingUiState -> {
        // W3 two-tap flow: User tapped before approving/denying on device
        ScreenModel(
          body = HardwareConfirmationResultBodyModel(
            headline = "Review update on Bitkey",
            subline = "Before updating, use your Bitkey device to review and approve the firmware update.",
            buttonText = "Got it",
            onAcknowledge = {
              // Return to awaiting confirmation to retry
              setState(
                AwaitingConfirmationUiState(
                  mcuUpdates = state.mcuUpdates,
                  currentMcuIndex = state.currentMcuIndex,
                  fetchResult = state.fetchResult,
                  resolvedDeviceInfo = state.resolvedDeviceInfo
                )
              )
            },
            eventTrackerScreenId = NFC_CONFIRMATION_PENDING
          ),
          presentationStyle = ScreenPresentationStyle.Modal
        )
      }

      is ConfirmationDeniedUiState -> {
        // W3 two-tap flow: Confirmation was not completed on device
        ScreenModel(
          body = HardwareConfirmationResultBodyModel(
            headline = "The update was not confirmed on your Bitkey",
            subline = "",
            buttonText = "OK",
            onAcknowledge = {
              // Return to beginning of flow (before first tap)
              setState(
                InNfcSessionUiState(
                  mcuUpdates = state.mcuUpdates,
                  currentMcuIndex = state.currentMcuIndex
                )
              )
            },
            eventTrackerScreenId = NFC_CONFIRMATION_DENIED
          ),
          presentationStyle = ScreenPresentationStyle.Modal
        )
      }
    }
  }

  private fun FwupNfcBodyModel.asFwupProgressScreen(designSystemV2Enabled: Boolean): ScreenModel =
    if (designSystemV2Enabled) {
      asPlatformNfcScreen(
        designSystemV2Enabled = true,
        devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform
      )
    } else {
      asFullScreen()
    }

  /**
   * Generates the screen model for Android-only UI states (NoNFC, EnableNFC instructions, navigation).
   * Handles NFC availability issues specific to Android platform.
   */
  @Composable
  private fun androidOnlyScreenModel(
    props: FwupNfcSessionUiProps,
    state: AndroidOnlyUiState,
    mcuUpdates: ImmutableList<McuFwupData>?,
    transactionType: FwupTransactionType,
    setState: (FwupNfcSessionUiState) -> Unit,
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
          mcuUpdates?.let {
            setState(InNfcSessionUiState(mcuUpdates = it, currentMcuIndex = transactionType.currentMcuIndex))
          } ?: error("No FWUP data available, this shouldn't happen")
        }
        NoNfcMessageModel(onBack = props.onBack)
          .asModalScreen()
      }
    }
  }

  /**
   * Extracts MCU firmware updates from the firmware update state.
   * Returns null if firmware is already up to date.
   */
  private fun extractMcuUpdates(
    state: FirmwareData.FirmwareUpdateState,
  ): ImmutableList<McuFwupData>? {
    return when (state) {
      is FirmwareData.FirmwareUpdateState.PendingUpdate -> state.mcuUpdates
      FirmwareData.FirmwareUpdateState.UpToDate -> null
    }
  }

  /**
   * Extracts whether hardware is fake from an AccountConfig.
   * Defaults to false for unknown account types.
   */
  private fun extractIsHardwareFake(accountConfig: AccountConfig): Boolean {
    return when (accountConfig) {
      is FullAccountConfig -> accountConfig.isHardwareFake
      is DefaultAccountConfig -> accountConfig.isHardwareFake
      else -> false
    }
  }

  /**
   * Extracts the hardware type from an AccountConfig.
   * Defaults to W1 if not specified or unknown account type.
   */
  private fun extractHardwareType(accountConfig: AccountConfig): HardwareType {
    return when (accountConfig) {
      is FullAccountConfig -> accountConfig.hardwareType
      is DefaultAccountConfig -> accountConfig.hardwareType ?: HardwareType.W1
      else -> HardwareType.W1
    }
  }

  /**
   * Determines the initial UI state based on NFC availability and firmware update data.
   * Handles Android-specific NFC states (not available, disabled) and transitions to searching state when enabled.
   */
  private fun determineInitialUiState(
    availability: NfcAvailability,
    mcuUpdates: ImmutableList<McuFwupData>?,
    transactionType: FwupTransactionType,
  ): FwupNfcSessionUiState {
    return when (availability) {
      NotAvailable -> NoNFCMessage
      Disabled -> EnableNFCInstructions
      Enabled -> when (mcuUpdates) {
        null -> error("No FWUP data available, this shouldn't happen")
        else -> {
          val clampedIndex = transactionType.currentMcuIndex.coerceIn(0, mcuUpdates.lastIndex)
          InNfcSessionUiState(mcuUpdates = mcuUpdates, currentMcuIndex = clampedIndex)
        }
      }
    }
  }

  /**
   * Handles NFC transaction failures and updates the UI state accordingly.
   */
  private suspend fun handleNfcTransactionFailure(
    error: NfcException,
    state: InNfcSessionUiState,
    continuation: (suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean>)?,
    props: FwupNfcSessionUiProps,
    setState: (FwupNfcSessionUiState) -> Unit,
  ) {
    when (error) {
      is NfcException.IOSOnly.UserCancellation -> props.onBack()
      is NfcException.UserDenied,
      is NfcException.ConfirmationNotCompleted -> handleUserDeniedOrConfirmationPending(error, state, continuation, props, setState, isDenied = true)
      is NfcException.ConfirmationPending -> handleUserDeniedOrConfirmationPending(error, state, continuation, props, setState, isDenied = false)
      is NfcException.IOSOnly.NoSession,
      is NfcException.CanBeRetried.SessionInvalidated -> {
        // Session gone mid-FWUP — on iOS this usually means the NFC coil is thermally
        // throttled. Show the cooldown screen so the coil can cool before the user
        // retries; progress is saved and resumes from the last successful sequence ID.
        if (fwupInProgress) {
          setState(
            NfcCooldownUiState(
              mcuUpdates = state.mcuUpdates,
              currentMcuIndex = state.currentMcuIndex
            )
          )
        } else {
          handleGenericError(error, state, props)
        }
      }
      else -> handleGenericError(error, state, props)
    }
  }

  private suspend fun handleUserDeniedOrConfirmationPending(
    error: NfcException,
    state: InNfcSessionUiState,
    continuation: (suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean>)?,
    props: FwupNfcSessionUiProps,
    setState: (FwupNfcSessionUiState) -> Unit,
    isDenied: Boolean,
  ) {
    if (continuation != null) {
      val newState = if (isDenied) {
        ConfirmationDeniedUiState(
          mcuUpdates = state.mcuUpdates,
          currentMcuIndex = state.currentMcuIndex,
          fetchResult = continuation,
          resolvedDeviceInfo = state.resolvedDeviceInfo
        )
      } else {
        ConfirmationPendingUiState(
          mcuUpdates = state.mcuUpdates,
          currentMcuIndex = state.currentMcuIndex,
          fetchResult = continuation,
          resolvedDeviceInfo = state.resolvedDeviceInfo
        )
      }
      setState(newState)
    } else {
      // Shouldn't happen, but fall back to normal error handling
      val errorType = if (isDenied) "UserDenied" else "ConfirmationPending"
      logWarn { "Received $errorType without continuation in FWUP flow" }
      handleGenericError(error, state, props)
    }
  }

  private suspend fun handleGenericError(
    error: NfcException,
    state: InNfcSessionUiState,
    props: FwupNfcSessionUiProps,
  ) {
    val inProgress = fwupInProgress
    val transactionType = when (inProgress) {
      true -> FwupTransactionType.ResumeFromSequenceId(
        sequenceId = getMcuSequenceId(state.currentMcu.mcuRole),
        currentMcuIndex = state.currentMcuIndex
      )
      false -> FwupTransactionType.StartFromBeginning(
        currentMcuIndex = state.currentMcuIndex
      )
    }
    eventTracker.track(
      Action.ACTION_APP_FWUP_MCU_UPDATE_FAILED,
      state.currentMcu.mcuRole.toEventTrackerContext()
    )
    props.onError(error, fwupInProgress, transactionType)
  }

  /**
   * Handles successful NFC transaction results and updates the UI state accordingly.
   */
  private fun handleNfcTransactionSuccess(
    result: FwupTransactionResult,
    state: InNfcSessionUiState,
    props: FwupNfcSessionUiProps,
    setProgress: (Float) -> Unit,
    setState: (FwupNfcSessionUiState) -> Unit,
  ) {
    when (result) {
      is FwupTransactionResult.Completed -> {
        if (!state.isLastMcu) {
          // Reset fwupInProgress so the next MCU starts fresh (version check,
          // fwupStart, etc.) rather than trying to resume the completed MCU's state.
          fwupInProgress = false
          setProgress(0.0f)
          setState(AwaitingNextMcuStartUiState(state.mcuUpdates, state.currentMcuIndex + 1))
        } else {
          setState(SuccessUiState(state.mcuUpdates, state.currentMcuIndex))
        }
      }
      is FwupTransactionResult.RequiresConfirmation -> {
        setState(
          AwaitingConfirmationUiState(
            mcuUpdates = state.mcuUpdates,
            currentMcuIndex = state.currentMcuIndex,
            fetchResult = result.fetchResult,
            resolvedDeviceInfo = result.resolvedDeviceInfo
          )
        )
      }
      is FwupTransactionResult.RequiresEmulatedPrompt -> {
        setState(
          InNfcSessionUiState(
            mcuUpdates = state.mcuUpdates,
            currentMcuIndex = state.currentMcuIndex,
            emulatedPrompt = result.emulatedPrompt
          )
        )
      }
      is FwupTransactionResult.PreviousMcuUpdateNotApplied -> {
        // The previous MCU update wasn't applied on the device. Return the user
        // to the start of the update flow so they can retry the full sequence.
        fwupInProgress = false
        setProgress(0.0f)
        props.onError(
          NfcException.PreviousMcuUpdateNotApplied(
            message = "Previous MCU ${result.mcuRole} update was not applied " +
              "(expected ${result.expectedVersion}, found ${result.actualVersion})"
          ),
          false,
          FwupTransactionType.StartFromBeginning()
        )
      }
    }
  }

  /**
   * Single NFC transaction effect that handles both initial transactions and continuations.
   *
   * @param state The active NFC session state. If [InNfcSessionUiState.fetchResult] is set,
   * this is a continuation from a two-tap flow and will call fetchResult then continue
   * with the FWUP transfer. Otherwise, starts a fresh FWUP transaction.
   */
  @Composable
  private fun NfcTransactionEffect(
    props: FwupNfcSessionUiProps,
    state: InNfcSessionUiState,
    isHardwareFake: Boolean,
    hardwareType: HardwareType,
    designSystemV2Enabled: Boolean,
    hiddenNfcScreenRevealDelayMs: Int,
    // TODO(W-8034): use Progress type.
    setProgress: (progress: Float) -> Unit,
    getCurrentState: () -> FwupNfcSessionUiState,
    setState: (FwupNfcSessionUiState) -> Unit,
  ) {
    val continuation = state.fetchResult
    // Include hardwareType in the key so the NFC session restarts if the config changes
    // (e.g., when activeOrDefaultConfig() emits the active account's config after initially
    // returning the fallback config). Also include continuation status for two-tap flows.
    val effectKey = "nfc-transaction-${state.currentMcuIndex}-${continuation != null}-$hardwareType"

    LaunchedEffect(effectKey) {
      if (state.displayMode == InNfcSessionUiState.DisplayMode.BackgroundRetryStartup) {
        // After the cooldown screen, keep rendering the same screen briefly while the next NFC
        // session starts in the background. In this failure mode iOS may immediately reject the
        // session again, and showing the app's searching screen right away causes a visible flash
        // back to the cooldown UI. The short delay gives the retry a chance to fail first, which
        // means there is no app-level visual flash at all. If the session survives, we then reveal
        // the normal searching UI.
        launch {
          delay(hiddenNfcScreenRevealDelayMs.milliseconds)
          if (getCurrentState() == state) {
            setState(
              state.copy(
                displayMode = InNfcSessionUiState.DisplayMode.Searching
              )
            )
          }
        }
      }
      delayForIosNativeNfcTransition(
        designSystemV2Enabled = designSystemV2Enabled,
        devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform
      )
      val hwPubKey = keyboxDao.activeKeybox().first().value?.activeHwKeyBundle?.authKey?.pubKey

      // FWUP does not handle SessionCanceled explicitly; platform cancellation
      // surfaces as an NfcException terminal event.
      nfcTransactor
        .transactEvents(
          parameters =
            NfcSession.Parameters(
              isHardwareFake = isHardwareFake,
              hardwareType = hardwareType,
              resolvedDeviceInfoOverride = state.resolvedDeviceInfo,
              needsAuthentication = true,
              shouldLock = true,
              skipFirmwareTelemetry = true,
              nfcFlowName = if (continuation != null) "fwup-confirmation" else "fwup",
              requirePairedHardware = hwPubKey?.let {
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
              } ?: RequirePairedHardware.NotRequired,
              asyncNfcSigning = false, // Unused for FWUP
              maxNfcRetryAttempts = nfcSessionRetryAttemptsFeatureFlag.intValue()
            ),
          transaction = { session, commands ->
            if (continuation != null) {
              // Continuation from two-tap flow: complete fwupStart then continue with transfer
              fwupContinuationTransaction(
                session = session,
                commands = commands,
                mcuFwupData = state.currentMcu,
                fetchResult = continuation,
                updateSequenceId = { sequenceId ->
                  setMcuSequenceId(state.currentMcu.mcuRole, sequenceId)
                  val progress =
                    fwupProgressCalculator.calculateProgress(
                      sequenceId = sequenceId,
                      finalSequenceId = state.currentMcu.finalSequenceId()
                    )
                  session.message = "${progress.roundToInt()}%"
                  setProgress(progress)
                }
              )
            } else {
              // Fresh start: run full fwupTransaction
              fwupTransaction(
                session = session,
                commands = commands,
                mcuFwupData = state.currentMcu,
                previousMcuFwupData = state.mcuUpdates.getOrNull(state.currentMcuIndex - 1),
                allMcuUpdates = state.mcuUpdates,
                updateSequenceId = { sequenceId ->
                  setMcuSequenceId(state.currentMcu.mcuRole, sequenceId)
                  val progress =
                    fwupProgressCalculator.calculateProgress(
                      sequenceId = sequenceId,
                      finalSequenceId = state.currentMcu.finalSequenceId()
                    )
                  session.message = "${progress.roundToInt()}%"
                  setProgress(progress)
                }
              )
            }
          },
          onEvent = { event ->
            // Per-event side effects (analytics, terminal handoff) — runs for
            // every event upstream of conflation. FWUP doesn't wire
            // SessionCanceled; cancellation surfaces as a Failed event.
            when (event) {
              is NfcTransactionEvent.TagConnected ->
                eventTracker.track(EventTrackerScreenInfo(NFC_DETECTED, FWUP))
              is NfcTransactionEvent.Succeeded<*> -> {
                @Suppress("UNCHECKED_CAST")
                handleNfcTransactionSuccess(
                  event.result as FwupTransactionResult,
                  state,
                  props,
                  setProgress,
                  setState
                )
              }
              is NfcTransactionEvent.Failed ->
                handleNfcTransactionFailure(event.error, state, continuation, props, setState)
              NfcTransactionEvent.TagDisconnected,
              NfcTransactionEvent.SessionCanceled,
              -> Unit
            }
          }
        ).collect { event ->
          // UI state mutation — Flow is already conflated by transactEvents.
          when (event) {
            is NfcTransactionEvent.TagConnected -> {
              when (val currentState = getCurrentState()) {
                is InNfcSessionUiState ->
                  setState(currentState.copy(displayMode = InNfcSessionUiState.DisplayMode.Updating))
                else -> Unit
              }
            }
            NfcTransactionEvent.TagDisconnected -> {
              when (val currentState = getCurrentState()) {
                is InNfcSessionUiState ->
                  setState(
                    currentState.copy(displayMode = InNfcSessionUiState.DisplayMode.LostConnection)
                  )
                else -> Unit
              }
            }
            NfcTransactionEvent.SessionCanceled,
            is NfcTransactionEvent.Succeeded<*>,
            is NfcTransactionEvent.Failed,
            -> Unit
          }
        }
    }
  }

  /**
   * Continuation transaction for two-tap flow: calls fetchResult to complete fwupStart,
   * then continues with the FWUP transfer and finish steps.
   */
  @Throws(NfcException::class, CancellationException::class)
  private suspend fun fwupContinuationTransaction(
    session: NfcSession,
    commands: NfcCommands,
    mcuFwupData: McuFwupData,
    fetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean>,
    updateSequenceId: suspend (sequenceId: UInt) -> Unit,
  ): FwupTransactionResult {
    // Complete the fwupStart by calling fetchResult
    val confirmResult = fetchResult(session, commands)
    val didStart = when (confirmResult) {
      is HardwareInteraction.Completed -> confirmResult.result
      else -> throw NfcException.CommandError(
        message = "Unexpected confirmation result: ${confirmResult::class.simpleName}"
      )
    }
    if (!didStart) {
      throw NfcException.CommandError(
        message = "fwup_start returned false after confirmation"
      )
    }
    fwupInProgress = true

    // Continue with the rest of the FWUP transaction
    fwupTransactionAfterStart(
      session = session,
      commands = commands,
      mcuFwupData = mcuFwupData,
      updateSequenceId = updateSequenceId
    )

    eventTracker.track(
      Action.ACTION_APP_FWUP_MCU_UPDATE_COMPLETE,
      mcuFwupData.mcuRole.toEventTrackerContext()
    )
    return FwupTransactionResult.Completed
  }

  @Throws(NfcException::class, CancellationException::class)
  @Suppress("ThrowsCount", "CyclomaticComplexMethod")
  private suspend fun fwupTransaction(
    session: NfcSession,
    commands: NfcCommands,
    mcuFwupData: McuFwupData,
    previousMcuFwupData: McuFwupData? = null,
    allMcuUpdates: List<McuFwupData> = emptyList(),
    updateSequenceId: suspend (sequenceId: UInt) -> Unit,
  ): FwupTransactionResult {
    val mcuRole = mcuFwupData.mcuRole

    if (!fwupInProgress) {
      // FWUP can succeed on device but fail during app confirmation,
      // causing users to retry an already-completed update. Skip if already at target.
      val currentDeviceInfo = commands.getDeviceInfo(session)

      // Verify previous MCU was actually applied before starting the next one.
      // This must run before the "already at target" skip below so that a
      // coincidentally up-to-date current MCU can't mask a failed previous MCU.
      val verificationFailure =
        verifyPreviousMcuApplied(currentDeviceInfo, previousMcuFwupData)
      if (verificationFailure != null) return verificationFailure

      // For W1 (single MCU), check main version field
      // For W3 (multi MCU), check specific MCU version from mcuInfo
      val currentMcuVersion = currentDeviceInfo.mcuInfo.find { it.mcuRole == mcuRole }?.firmwareVersion
        ?: currentDeviceInfo.version

      if (currentMcuVersion == mcuFwupData.version) {
        logWarn { "MCU $mcuRole already at target version ${mcuFwupData.version}, skipping update" }
        eventTracker.track(
          Action.ACTION_APP_FWUP_MCU_UPDATE_SKIPPED,
          mcuRole.toEventTrackerContext()
        )
        return FwupTransactionResult.Completed
      }

      // We have to maintain `fwupInProgress` and reset the sequence ID due to some unfortunate
      // side effects with the `fwup_start` command in delta mode. In short: the app can't tell
      // if the firmware update has started on the firmware or not, because there is no NFC command
      // for that. But at the same time, the app must send `fwup_start` if the firmware hasn't
      // begun the FWUP, and it must NOT send `fwup_start` if it has. This is fixable in firmware
      // but the code must be this way for now.
      setMcuSequenceId(mcuRole, 0u)

      // Opt into atomic FWUP when updating UXC as part of a multi-MCU update AND the
      // other MCU (Core) actually still needs updating. If Core is already at its target
      // version, it will be skipped and we shouldn't leave UXC in a deferred state.
      // Old firmware ignores this field (proto3 default false).
      val deferCommit = mcuRole == build.wallet.firmware.McuRole.UXC &&
        allMcuUpdates.any { otherMcu ->
          otherMcu.mcuRole != mcuRole &&
            currentDeviceInfo.mcuInfo.find { it.mcuRole == otherMcu.mcuRole }
              ?.firmwareVersion != otherMcu.version
        }

      val startResult =
        commands.fwupStart(
          session = session,
          patchSize =
            when (mcuFwupData.fwupMode) {
              FwupMode.Normal -> null
              FwupMode.Delta -> mcuFwupData.firmware.size.toUInt()
            },
          fwupMode = mcuFwupData.fwupMode,
          mcuRole = mcuRole,
          version = mcuFwupData.version,
          deferCommit = deferCommit
        )

      val didStart = when (startResult) {
        is HardwareInteraction.Completed -> startResult.result
        is HardwareInteraction.RequiresConfirmation -> {
          // W3 two-tap flow: firmware requires user confirmation before continuing
          return FwupTransactionResult.RequiresConfirmation(
            fetchResult = startResult.toSessionFn(),
            resolvedDeviceInfo = commands.detectedDeviceInfo(session)
          )
        }
        is HardwareInteraction.ConfirmWithEmulatedPrompt -> {
          // Fake hardware emulated prompt - show prompt selection UI
          return FwupTransactionResult.RequiresEmulatedPrompt(startResult)
        }
        is HardwareInteraction.RequiresTransfer -> {
          // RequiresTransfer is only for transaction signing, should never happen in fwup
          throw NfcException.CommandError(
            message = "Unexpected RequiresTransfer in fwup context"
          )
        }
      }

      if (!didStart) {
        throw NfcException.CommandError(
          message = "fwup_start returned false for MCU $mcuRole"
        )
      }

      eventTracker.track(
        Action.ACTION_APP_FWUP_MCU_UPDATE_STARTED,
        mcuRole.toEventTrackerContext()
      )
      fwupInProgress = true
    } else {
      // FWUP is already in progress (resuming after tag loss). Send a lightweight
      // getDeviceInfo command first to ensure the NFC connection is stable before
      // sending fwupTransfer chunks. Without this, the first fwupTransfer can fail
      // immediately on Android because the tag connection isn't fully established yet.
      commands.getDeviceInfo(session)
    }

    var sequenceId = getMcuSequenceId(mcuRole)

    while (sequenceId <= mcuFwupData.finalSequenceId()) {
      val off = (sequenceId * mcuFwupData.chunkSize).toInt()
      val size = mcuFwupData.chunkSize.toInt().coerceAtMost(mcuFwupData.firmware.size - off)
      val chunk = mcuFwupData.firmware.toByteArray().copyOfRange(off, off + size)

      val didTransfer =
        commands.fwupTransfer(
          session = session,
          sequenceId = sequenceId,
          fwupData = chunk.toUByteList(),
          offset = 0U,
          fwupMode = mcuFwupData.fwupMode,
          mcuRole = mcuRole
        )

      sequenceId += 1u

      // Send back the new sequence ID if it was successful so it's remembered
      // for if we lose connection and also to update the progress UI
      if (didTransfer) {
        updateSequenceId(sequenceId)
      } else {
        // Early return if failed to transfer
        throw NfcException.CommandError(
          message = "fwup_transfer failed for MCU $mcuRole at sequence ${sequenceId - 1u}"
        )
      }
    }

    // Final transfer: Transfer signature to the fixed offset
    val didTransfer =
      commands.fwupTransfer(
        session = session,
        sequenceId = 0u,
        fwupData = mcuFwupData.signature.toUByteList(),
        offset = mcuFwupData.signatureOffset,
        // Delta or not, the last transfer of the signature is always a "normal" transfer.
        fwupMode = FwupMode.Normal,
        mcuRole = mcuRole
      )

    // Early return if failed to transfer the final transfer
    if (!didTransfer) {
      throw NfcException.CommandError(
        message = "fwup_transfer signature failed for MCU $mcuRole"
      )
    }

    // Finish
    val finishResult =
      commands.fwupFinish(
        session = session,
        appPropertiesOffset = mcuFwupData.appPropertiesOffset,
        signatureOffset = mcuFwupData.signatureOffset,
        fwupMode = mcuFwupData.fwupMode,
        mcuRole = mcuRole
      )

    fwupInProgress = false

    return when (finishResult) {
      Unspecified, SignatureInvalid, VersionInvalid, ConfirmationMismatch, Error ->
        throw NfcException.FwupFinishError(
          status = finishResult,
          message = "fwup_finish failed for MCU $mcuRole: $finishResult"
        )
      Success, WillApplyPatch -> {
        eventTracker.track(
          Action.ACTION_APP_FWUP_MCU_UPDATE_COMPLETE,
          mcuRole.toEventTrackerContext()
        )
        FwupTransactionResult.Completed
      }
      Unauthenticated ->
        throw NfcException.CommandErrorUnauthenticated()
    }
  }

  /**
   * Verify that the previous MCU's update was actually applied by checking its
   * version in [FirmwareDeviceInfo.mcuInfo]. Returns a [FwupTransactionResult]
   * if verification fails, or `null` if the update was applied (or can't be checked).
   *
   * Old firmware that doesn't report per-MCU versions ([mcuInfo] empty) is allowed
   * to proceed without verification.
   */
  private fun verifyPreviousMcuApplied(
    currentDeviceInfo: FirmwareDeviceInfo,
    previousMcuFwupData: McuFwupData?,
  ): FwupTransactionResult? {
    if (previousMcuFwupData == null || currentDeviceInfo.mcuInfo.isEmpty()) return null

    val previousMcuVersion = currentDeviceInfo.mcuInfo
      .find { it.mcuRole == previousMcuFwupData.mcuRole }?.firmwareVersion

    if (previousMcuVersion != null && previousMcuVersion != previousMcuFwupData.version) {
      logError {
        "Previous MCU ${previousMcuFwupData.mcuRole} update was not applied. " +
          "Expected version ${previousMcuFwupData.version} but found $previousMcuVersion."
      }
      eventTracker.track(
        Action.ACTION_APP_FWUP_MCU_UPDATE_FAILED,
        previousMcuFwupData.mcuRole.toEventTrackerContext()
      )
      return FwupTransactionResult.PreviousMcuUpdateNotApplied(
        mcuRole = previousMcuFwupData.mcuRole,
        expectedVersion = previousMcuFwupData.version,
        actualVersion = previousMcuVersion
      )
    }
    return null
  }

  /**
   * Performs the FWUP transfer and finish steps (after fwupStart has completed).
   * Used by ConfirmationNfcTransactionEffect after the two-tap flow completes fwupStart.
   */
  @Throws(NfcException::class, CancellationException::class)
  @Suppress("ThrowsCount")
  private suspend fun fwupTransactionAfterStart(
    session: NfcSession,
    commands: NfcCommands,
    mcuFwupData: McuFwupData,
    updateSequenceId: suspend (sequenceId: UInt) -> Unit,
  ) {
    val mcuRole = mcuFwupData.mcuRole
    var sequenceId = getMcuSequenceId(mcuRole)

    while (sequenceId <= mcuFwupData.finalSequenceId()) {
      val off = (sequenceId * mcuFwupData.chunkSize).toInt()
      val size = mcuFwupData.chunkSize.toInt().coerceAtMost(mcuFwupData.firmware.size - off)
      val chunk = mcuFwupData.firmware.toByteArray().copyOfRange(off, off + size)

      val didTransfer =
        commands.fwupTransfer(
          session = session,
          sequenceId = sequenceId,
          fwupData = chunk.toUByteList(),
          offset = 0U,
          fwupMode = mcuFwupData.fwupMode,
          mcuRole = mcuRole
        )

      sequenceId += 1u

      if (didTransfer) {
        updateSequenceId(sequenceId)
      } else {
        throw NfcException.CommandError(
          message = "fwup_transfer failed for MCU $mcuRole at sequence ${sequenceId - 1u}"
        )
      }
    }

    // Final transfer: Transfer signature
    val didTransfer =
      commands.fwupTransfer(
        session = session,
        sequenceId = 0u,
        fwupData = mcuFwupData.signature.toUByteList(),
        offset = mcuFwupData.signatureOffset,
        fwupMode = FwupMode.Normal,
        mcuRole = mcuRole
      )

    if (!didTransfer) {
      throw NfcException.CommandError(
        message = "fwup_transfer signature failed for MCU $mcuRole"
      )
    }

    // Finish
    val finishResult =
      commands.fwupFinish(
        session = session,
        appPropertiesOffset = mcuFwupData.appPropertiesOffset,
        signatureOffset = mcuFwupData.signatureOffset,
        fwupMode = mcuFwupData.fwupMode,
        mcuRole = mcuRole
      )

    fwupInProgress = false

    return when (finishResult) {
      Unspecified, SignatureInvalid, VersionInvalid, ConfirmationMismatch, Error ->
        throw NfcException.FwupFinishError(
          status = finishResult,
          message = "fwup_finish failed for MCU $mcuRole: $finishResult"
        )
      Success, WillApplyPatch ->
        Unit
      Unauthenticated ->
        throw NfcException.CommandErrorUnauthenticated()
    }
  }

  private suspend fun getMcuSequenceId(mcuRole: build.wallet.firmware.McuRole): UInt =
    fwupDataDao.getMcuSequenceId(mcuRole)
      .logFailure { "Failed to get fwup sequence ID for MCU $mcuRole, using 0 as default." }
      .getOrElse { 0u }

  private suspend fun setMcuSequenceId(
    mcuRole: build.wallet.firmware.McuRole,
    sequenceId: UInt,
  ) {
    fwupDataDao.setMcuSequenceId(mcuRole, sequenceId)
  }
}

private sealed interface FwupNfcSessionUiState {
  sealed class InSessionUiState(
    open val mcuUpdates: ImmutableList<McuFwupData>,
    open val currentMcuIndex: Int = 0,
  ) : FwupNfcSessionUiState {
    /** The MCU currently being updated */
    val currentMcu: McuFwupData get() = mcuUpdates[currentMcuIndex]

    /** True if this is the last MCU to update */
    val isLastMcu: Boolean get() = currentMcuIndex == mcuUpdates.size - 1

    /** Total number of MCUs to update */
    val totalMcus: Int get() = mcuUpdates.size

    /**
     * Active NFC session state. The effect stays alive while in this state, regardless
     * of [displayMode] changes. This mirrors [NfcConfirmableSessionUiStateMachine.InNfcSession].
     *
     * @param fetchResult If set, this is a continuation from a two-tap flow (W3 confirmation
     * or emulated prompt). The NFC transaction will call this instead of starting fresh.
     * @param displayMode Controls what UI to show (searching, updating progress, lost connection).
     */
    data class InNfcSessionUiState(
      override val mcuUpdates: ImmutableList<McuFwupData>,
      override val currentMcuIndex: Int = 0,
      val fetchResult: (suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean>)? = null,
      val resolvedDeviceInfo: FirmwareDeviceInfo? = null,
      val displayMode: DisplayMode = DisplayMode.Searching,
      val emulatedPrompt: HardwareInteraction.ConfirmWithEmulatedPrompt<Boolean>? = null,
    ) : InSessionUiState(mcuUpdates, currentMcuIndex) {
      enum class DisplayMode {
        /**
         * The app-level NFC flow is active and waiting for the customer to present Bitkey.
         */
        Searching,

        /**
         * Bitkey is connected and the firmware update transaction is actively progressing.
         */
        Updating,

        /**
         * The tag was disconnected after the session was established, so the user needs to
         * reconnect Bitkey to continue the current update attempt.
         */
        LostConnection,

        /**
         * After the cooldown screen, start the next NFC session in the background but keep the
         * cooldown UI visible briefly. This avoids flashing the app's searching screen when iOS
         * immediately rejects the retry session.
         */
        BackgroundRetryStartup,
      }
    }

    data class SuccessUiState(
      override val mcuUpdates: ImmutableList<McuFwupData>,
      override val currentMcuIndex: Int = 0,
    ) : InSessionUiState(mcuUpdates, currentMcuIndex)

    /**
     * W3 two-tap confirmation flow: fwupStart returned RequiresConfirmation.
     * User must confirm on device and tap again to continue.
     */
    data class AwaitingConfirmationUiState(
      override val mcuUpdates: ImmutableList<McuFwupData>,
      override val currentMcuIndex: Int = 0,
      val fetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean>,
      val resolvedDeviceInfo: FirmwareDeviceInfo? = null,
    ) : InSessionUiState(mcuUpdates, currentMcuIndex)

    /**
     * Shown between MCU updates in a sequence. Tells user the previous component is done
     * and prompts them to start the next component update.
     * Flow: User taps continue → NFC session → fwupStart → device confirmation → transfer
     *
     * TODO: Remove this intermediate screen once firmware is updated to not require
     *  confirmation for individual MCUs in a sequenced update.
     */
    data class AwaitingNextMcuStartUiState(
      override val mcuUpdates: ImmutableList<McuFwupData>,
      override val currentMcuIndex: Int,
    ) : InSessionUiState(mcuUpdates, currentMcuIndex)

    /**
     * iOS-only: the active FWUP session is gone and we need to wait briefly before letting the
     * user continue. Progress is preserved and the next attempt resumes from the saved sequence ID.
     */
    data class NfcCooldownUiState(
      override val mcuUpdates: ImmutableList<McuFwupData>,
      override val currentMcuIndex: Int = 0,
    ) : InSessionUiState(mcuUpdates, currentMcuIndex)

    /**
     * W3 two-tap flow: User tapped before approving/denying on device.
     * Shows prompt to make decision on device.
     */
    data class ConfirmationPendingUiState(
      override val mcuUpdates: ImmutableList<McuFwupData>,
      override val currentMcuIndex: Int = 0,
      val fetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean>,
      val resolvedDeviceInfo: FirmwareDeviceInfo? = null,
    ) : InSessionUiState(mcuUpdates, currentMcuIndex)

    /**
     * W3 two-tap flow: User explicitly denied on device.
     * Shows acknowledgment screen before returning to confirmation flow.
     */
    data class ConfirmationDeniedUiState(
      override val mcuUpdates: ImmutableList<McuFwupData>,
      override val currentMcuIndex: Int = 0,
      val fetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean>,
      val resolvedDeviceInfo: FirmwareDeviceInfo? = null,
    ) : InSessionUiState(mcuUpdates, currentMcuIndex)
  }

  // TODO (W-4558): Consolidate these states with those in [NfcSessionUiStateMachineImpl]
  sealed interface AndroidOnlyUiState : FwupNfcSessionUiState {
    /** Showing a message for mobile devices that don't have NFC -- Android-only. */
    data object NoNFCMessage : AndroidOnlyUiState

    /** Showing a message for when NFC is not enabled -- Android-only. */
    data object EnableNFCInstructions : AndroidOnlyUiState

    /** Navigating to the settings screen for enabling NFC -- Android-only. */
    data object NavigateToEnableNFC : AndroidOnlyUiState
  }
}

/**
 * Describes the type of FWUP transaction to attempt, based on whether FWUP has already
 * been started or not. Used when NFC tag connection is lost and reconnected in the
 * middle of FWUP.
 */
sealed interface FwupTransactionType {
  /** The MCU index to start/resume at in a multi-MCU sequence. */
  val currentMcuIndex: Int

  /** Start FWUP from the beginning at the given [currentMcuIndex]. */
  data class StartFromBeginning(
    override val currentMcuIndex: Int = 0,
  ) : FwupTransactionType

  /** Resume FWUP from the given [sequenceId] at the given [currentMcuIndex]. */
  data class ResumeFromSequenceId(
    val sequenceId: UInt,
    override val currentMcuIndex: Int = 0,
  ) : FwupTransactionType
}

/**
 * Converts McuRole to FwupMcuEventTrackerContext for analytics tracking.
 */
private fun build.wallet.firmware.McuRole.toEventTrackerContext(): FwupMcuEventTrackerContext =
  when (this) {
    build.wallet.firmware.McuRole.CORE -> FwupMcuEventTrackerContext.CORE
    build.wallet.firmware.McuRole.UXC -> FwupMcuEventTrackerContext.UXC
  }

/**
 * Result type for [FwupNfcSessionUiStateMachineImpl.fwupTransaction] to signal
 * whether the transaction completed or requires user interaction.
 */
internal sealed interface FwupTransactionResult {
  /** Transaction completed successfully (either fully or skipped because already at target version). */
  data object Completed : FwupTransactionResult

  /** W3 two-tap flow: fwupStart requires user confirmation on device before continuing. */
  data class RequiresConfirmation(
    val fetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<Boolean>,
    val resolvedDeviceInfo: FirmwareDeviceInfo? = null,
  ) : FwupTransactionResult

  /** Fake hardware: fwupStart returned emulated prompt for user selection. */
  data class RequiresEmulatedPrompt(
    val emulatedPrompt: HardwareInteraction.ConfirmWithEmulatedPrompt<Boolean>,
  ) : FwupTransactionResult

  /**
   * The previous MCU's firmware update was not applied on the device.
   * Detected by reading the device info at the start of the next MCU's update
   * and finding the previous MCU's version hasn't changed to the expected target.
   * The user should be returned to the start of the update flow to retry.
   */
  data class PreviousMcuUpdateNotApplied(
    val mcuRole: build.wallet.firmware.McuRole,
    val expectedVersion: String,
    val actualVersion: String,
  ) : FwupTransactionResult
}
