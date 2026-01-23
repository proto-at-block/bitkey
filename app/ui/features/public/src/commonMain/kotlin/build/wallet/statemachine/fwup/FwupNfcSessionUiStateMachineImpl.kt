package build.wallet.statemachine.fwup

import androidx.compose.runtime.*
import bitkey.account.*
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
import build.wallet.feature.flags.NfcSessionRetryAttemptsFeatureFlag
import build.wallet.feature.intValue
import build.wallet.fwup.*
import build.wallet.fwup.FwupFinishResponseStatus.*
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.logFailure
import build.wallet.logging.logWarn
import build.wallet.nfc.*
import build.wallet.nfc.NfcAvailability.Available.Disabled
import build.wallet.nfc.NfcAvailability.Available.Enabled
import build.wallet.nfc.NfcAvailability.NotAvailable
import build.wallet.nfc.NfcSession.RequirePairedHardware
import build.wallet.nfc.platform.EmulatedPromptOption
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.NfcCommands
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.fwup.FwupNfcSessionUiState.AndroidOnlyUiState
import build.wallet.statemachine.fwup.FwupNfcSessionUiState.AndroidOnlyUiState.*
import build.wallet.statemachine.fwup.FwupNfcSessionUiState.InSessionUiState
import build.wallet.statemachine.fwup.FwupNfcSessionUiState.InSessionUiState.*
import build.wallet.statemachine.nfc.EnableNfcInstructionsModel
import build.wallet.statemachine.nfc.NfcSuccessScreenDuration
import build.wallet.statemachine.nfc.NoNfcMessageModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.platform.nfc.EnableNfcNavigator
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiProps
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiStateMachine
import build.wallet.toUByteList
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

@Suppress("LargeClass")
@BitkeyInject(ActivityScope::class)
class FwupNfcSessionUiStateMachineImpl(
  private val enableNfcNavigator: EnableNfcNavigator,
  private val eventTracker: EventTracker,
  private val fwupProgressCalculator: FwupProgressCalculator,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val nfcReaderCapability: NfcReaderCapability,
  private val nfcTransactor: NfcTransactor,
  private val fwupDataDaoProvider: FwupDataDaoProvider,
  private val firmwareDataService: FirmwareDataService,
  private val accountConfigService: AccountConfigService,
  private val keyboxDao: KeyboxDao,
  private val signatureVerifier: SignatureVerifier,
  private val nfcSessionRetryAttemptsFeatureFlag: NfcSessionRetryAttemptsFeatureFlag,
  private val hardwareConfirmationUiStateMachine: HardwareConfirmationUiStateMachine,
) : FwupNfcSessionUiStateMachine {
  private val secureRandom = SecureRandom()
  private var fwupInProgress = false

  @Composable
  override fun model(props: FwupNfcSessionUiProps): ScreenModel {
    val scope = rememberStableCoroutineScope()
    val accountConfig = remember { accountConfigService.activeOrDefaultConfig().value }
    val mcuUpdates by remember {
      firmwareDataService.firmwareData().mapAsStateFlow(scope) {
        extractMcuUpdates(it.firmwareUpdateState)
      }
    }.collectAsState()
    val isHardwareFake = remember { determineIsHardwareFake(accountConfig) }
    val hardwareType = remember { determineHardwareType(accountConfig) }

    var uiState by remember {
      mutableStateOf(
        determineInitialUiState(
          availability = nfcReaderCapability.availability(isHardwareFake),
          mcuUpdates = mcuUpdates
        )
      )
    }

    var fwupProgress by remember { mutableStateOf(0.0f) }

    return when (val state = uiState) {
      is InSessionUiState -> inSessionScreenModel(
        props = props,
        state = state,
        isHardwareFake = isHardwareFake,
        hardwareType = hardwareType,
        fwupProgress = fwupProgress,
        setProgress = { fwupProgress = it },
        setState = { uiState = it }
      )

      is AndroidOnlyUiState -> androidOnlyScreenModel(
        props = props,
        state = state,
        mcuUpdates = mcuUpdates,
        setState = { uiState = it }
      )
    }
  }

  /**
   * Generates the screen model for in-session UI states (Searching, Updating, LostConnection, Success).
   * Manages NFC transaction effects and progress tracking during firmware updates.
   */
  @Composable
  private fun inSessionScreenModel(
    props: FwupNfcSessionUiProps,
    state: InSessionUiState,
    isHardwareFake: Boolean,
    hardwareType: HardwareType,
    fwupProgress: Float,
    setProgress: (Float) -> Unit,
    setState: (FwupNfcSessionUiState) -> Unit,
  ): ScreenModel {
    return when (state) {
      is InNfcSessionUiState -> {
        // NfcTransactionEffect stays alive for the entire InNfcSessionUiState,
        // regardless of displayMode changes (Searching -> Updating -> LostConnection)
        NfcTransactionEffect(
          props = props,
          state = state,
          isHardwareFake = isHardwareFake,
          hardwareType = hardwareType,
          setProgress = setProgress,
          setState = setState
        )

        when (state.displayMode) {
          InNfcSessionUiState.DisplayMode.Searching -> {
            FwupNfcBodyModel(
              onCancel = props.onBack,
              status = FwupNfcBodyModel.Status.Searching(),
              eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_INITIATE, FWUP)
            ).asFullScreen()
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
              eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_UPDATE_IN_PROGRESS_FWUP)
            ).asFullScreen()
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
              eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_DEVICE_LOST_CONNECTION_FWUP)
            ).asPlatformNfcScreen()
          }
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
          eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_SUCCESS, FWUP)
        ).asPlatformNfcScreen()
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
                  mcuUpdates = state.mcuUpdates,
                  currentMcuIndex = state.currentMcuIndex,
                  fetchResult = state.fetchResult
                )
              )
            }
          )
        )
      }

      is EmulatingPromptUiState -> {
        val scope = rememberStableCoroutineScope()
        PromptSelectionFormBodyModel(
          options = state.options.map { it.name },
          onOptionSelected = { selectedIndex ->
            val selectedOption = state.options[selectedIndex]
            scope.launch {
              selectedOption.onSelect?.invoke()
              // If "Deny" was selected, cancel the flow instead of continuing
              if (selectedOption.name == EmulatedPromptOption.DENY) {
                props.onBack()
              } else {
                // Transition to InNfcSessionUiState with fetchResult to start
                // a new NFC session for the continuation
                setState(
                  InNfcSessionUiState(
                    mcuUpdates = state.mcuUpdates,
                    currentMcuIndex = state.currentMcuIndex,
                    fetchResult = selectedOption.fetchResult
                  )
                )
              }
            }
          },
          onBack = props.onBack,
          eventTrackerContext = FWUP
        ).asModalScreen()
      }
    }
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
            setState(InNfcSessionUiState(mcuUpdates = it, currentMcuIndex = 0))
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
   * Determines whether the hardware is using a fake implementation based on account configuration.
   * Defaults to false for unknown account types.
   */
  private fun determineIsHardwareFake(accountConfig: AccountConfig?): Boolean {
    return when (accountConfig) {
      is FullAccountConfig -> accountConfig.isHardwareFake
      is DefaultAccountConfig -> accountConfig.isHardwareFake
      else -> false
    }
  }

  /**
   * Determines the hardware type from account configuration.
   * Defaults to W1 if not specified or unknown account type.
   */
  private fun determineHardwareType(accountConfig: AccountConfig?): HardwareType {
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
  ): FwupNfcSessionUiState {
    return when (availability) {
      NotAvailable -> NoNFCMessage
      Disabled -> EnableNFCInstructions
      Enabled -> when (mcuUpdates) {
        null -> error("No FWUP data available, this shouldn't happen")
        else -> InNfcSessionUiState(mcuUpdates = mcuUpdates, currentMcuIndex = 0)
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
    // TODO(W-8034): use Progress type.
    setProgress: (progress: Float) -> Unit,
    setState: (FwupNfcSessionUiState) -> Unit,
  ) {
    val continuation = state.fetchResult
    // Include whether this is a continuation in the key so a fresh NFC session starts
    val effectKey = "nfc-transaction-${state.currentMcuIndex}-${continuation != null}"

    LaunchedEffect(effectKey) {
      val hwPubKey = keyboxDao.activeKeybox().first().value?.activeHwKeyBundle?.authKey?.pubKey
      nfcTransactor
        .transact(
          parameters =
            NfcSession.Parameters(
              isHardwareFake = isHardwareFake,
              hardwareType = hardwareType,
              needsAuthentication = true,
              shouldLock = true,
              skipFirmwareTelemetry = true,
              nfcFlowName = if (continuation != null) "fwup-confirmation" else "fwup",
              onTagConnected = {
                eventTracker.track(EventTrackerScreenInfo(NFC_DETECTED, FWUP))
                setState(state.copy(displayMode = InNfcSessionUiState.DisplayMode.Updating))
              },
              onTagDisconnected = {
                setState(state.copy(displayMode = InNfcSessionUiState.DisplayMode.LostConnection))
              },
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
          }
        ).onFailure { error ->
          when (error) {
            is NfcException.IOSOnly.UserCancellation -> {
              props.onBack()
            }
            else -> {
              val inProgress = fwupInProgress
              val transactionType = when (inProgress) {
                true -> FwupTransactionType.ResumeFromSequenceId(getMcuSequenceId(state.currentMcu.mcuRole))
                false -> FwupTransactionType.StartFromBeginning
              }
              eventTracker.track(
                Action.ACTION_APP_FWUP_MCU_UPDATE_FAILED,
                state.currentMcu.mcuRole.toEventTrackerContext()
              )
              props.onError(error, fwupInProgress, transactionType)
            }
          }
        }.onSuccess { result ->
          when (result) {
            is FwupTransactionResult.Completed -> {
              // Check if there are more MCUs to update
              if (!state.isLastMcu) {
                // Move to next MCU (fresh start, no fetchResult)
                setProgress(0.0f)
                setState(InNfcSessionUiState(state.mcuUpdates, state.currentMcuIndex + 1))
              } else {
                // All MCUs updated successfully
                setState(SuccessUiState(state.mcuUpdates, state.currentMcuIndex))
              }
            }
            is FwupTransactionResult.RequiresConfirmation -> {
              // W3 two-tap flow: transition to awaiting confirmation state
              setState(
                AwaitingConfirmationUiState(
                  mcuUpdates = state.mcuUpdates,
                  currentMcuIndex = state.currentMcuIndex,
                  fetchResult = result.fetchResult
                )
              )
            }
            is FwupTransactionResult.RequiresEmulatedPrompt -> {
              // Fake hardware: transition to emulated prompt selection state
              setState(
                EmulatingPromptUiState(
                  mcuUpdates = state.mcuUpdates,
                  currentMcuIndex = state.currentMcuIndex,
                  options = result.options
                )
              )
            }
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
      throw NfcException.CommandError()
    }
    fwupInProgress = true

    // Continue with the rest of the FWUP transaction
    fwupTransactionAfterStart(
      session = session,
      commands = commands,
      mcuFwupData = mcuFwupData,
      updateSequenceId = updateSequenceId
    )

    return FwupTransactionResult.Completed
  }

  @Throws(NfcException::class, CancellationException::class)
  @Suppress("ThrowsCount")
  private suspend fun fwupTransaction(
    session: NfcSession,
    commands: NfcCommands,
    mcuFwupData: McuFwupData,
    updateSequenceId: suspend (sequenceId: UInt) -> Unit,
  ): FwupTransactionResult {
    val mcuRole = mcuFwupData.mcuRole

    if (!fwupInProgress) {
      // FWUP can succeed on device but fail during app confirmation,
      // causing users to retry an already-completed update. Skip if already at target.
      val currentDeviceInfo = commands.getDeviceInfo(session)

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

      val startResult =
        commands.fwupStart(
          session = session,
          patchSize =
            when (mcuFwupData.fwupMode) {
              FwupMode.Normal -> null
              FwupMode.Delta -> mcuFwupData.firmware.size.toUInt()
            },
          fwupMode = mcuFwupData.fwupMode,
          mcuRole = mcuRole
        )

      val didStart = when (startResult) {
        is HardwareInteraction.Completed -> startResult.result
        is HardwareInteraction.RequiresConfirmation -> {
          // W3 two-tap flow: firmware requires user confirmation before continuing
          return FwupTransactionResult.RequiresConfirmation(startResult.fetchResult)
        }
        is HardwareInteraction.ConfirmWithEmulatedPrompt -> {
          // Fake hardware emulated prompt - show prompt selection UI
          return FwupTransactionResult.RequiresEmulatedPrompt(startResult.options)
        }
      }

      if (!didStart) {
        throw NfcException.CommandError()
      }

      eventTracker.track(
        Action.ACTION_APP_FWUP_MCU_UPDATE_STARTED,
        mcuRole.toEventTrackerContext()
      )
      fwupInProgress = true
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
        throw NfcException.CommandError()
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
      throw NfcException.CommandError()
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
      Unspecified, SignatureInvalid, VersionInvalid, Error ->
        throw NfcException.CommandError()
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
        throw NfcException.CommandError()
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
      throw NfcException.CommandError()
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
      Unspecified, SignatureInvalid, VersionInvalid, Error ->
        throw NfcException.CommandError()
      Success, WillApplyPatch ->
        Unit
      Unauthenticated ->
        throw NfcException.CommandErrorUnauthenticated()
    }
  }

  private suspend fun getMcuSequenceId(mcuRole: build.wallet.firmware.McuRole): UInt =
    fwupDataDaoProvider.get().value.getMcuSequenceId(mcuRole)
      .logFailure { "Failed to get fwup sequence ID for MCU $mcuRole, using 0 as default." }
      .getOrElse { 0u }

  private suspend fun setMcuSequenceId(
    mcuRole: build.wallet.firmware.McuRole,
    sequenceId: UInt,
  ) {
    fwupDataDaoProvider.get().value.setMcuSequenceId(mcuRole, sequenceId)
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
      val displayMode: DisplayMode = DisplayMode.Searching,
    ) : InSessionUiState(mcuUpdates, currentMcuIndex) {
      enum class DisplayMode { Searching, Updating, LostConnection }
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
    ) : InSessionUiState(mcuUpdates, currentMcuIndex)

    /**
     * Fake hardware emulated prompt: fwupStart returned ConfirmWithEmulatedPrompt.
     * Display prompt options to simulate device confirmation.
     */
    data class EmulatingPromptUiState(
      override val mcuUpdates: ImmutableList<McuFwupData>,
      override val currentMcuIndex: Int = 0,
      val options: List<EmulatedPromptOption<Boolean>>,
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
  /** Start FWUP from the beginning */
  data object StartFromBeginning : FwupTransactionType

  /** Resume FWUP from the given [sequenceId] */
  data class ResumeFromSequenceId(
    val sequenceId: UInt,
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
  ) : FwupTransactionResult

  /** Fake hardware: fwupStart returned emulated prompt options for user selection. */
  data class RequiresEmulatedPrompt(
    val options: List<EmulatedPromptOption<Boolean>>,
  ) : FwupTransactionResult
}
