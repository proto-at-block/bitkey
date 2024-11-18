package build.wallet.statemachine.fwup

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.FWUP
import build.wallet.analytics.events.screen.id.FwupEventTrackerScreenId.NFC_DEVICE_LOST_CONNECTION_FWUP
import build.wallet.analytics.events.screen.id.FwupEventTrackerScreenId.NFC_UPDATE_IN_PROGRESS_FWUP
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId.*
import build.wallet.analytics.v1.Action
import build.wallet.fwup.*
import build.wallet.fwup.FwupFinishResponseStatus.*
import build.wallet.logging.log
import build.wallet.nfc.NfcAvailability.Available.Disabled
import build.wallet.nfc.NfcAvailability.Available.Enabled
import build.wallet.nfc.NfcAvailability.NotAvailable
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcReaderCapability
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcTransactor
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
import build.wallet.statemachine.platform.nfc.EnableNfcNavigator
import build.wallet.time.Delayer
import build.wallet.toUByteList
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

class FwupNfcSessionUiStateMachineImpl(
  private val enableNfcNavigator: EnableNfcNavigator,
  private val eventTracker: EventTracker,
  private val delayer: Delayer,
  private val fwupProgressCalculator: FwupProgressCalculator,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val nfcReaderCapability: NfcReaderCapability,
  private val nfcTransactor: NfcTransactor,
  private val fwupDataDao: FwupDataDao,
  private val firmwareDataService: FirmwareDataService,
) : FwupNfcSessionUiStateMachine {
  private var fwupInProgress = false

  @Composable
  override fun model(props: FwupNfcSessionUiProps): ScreenModel {
    var uiState by remember {
      mutableStateOf(
        when (nfcReaderCapability.availability(props.isHardwareFake)) {
          NotAvailable -> NoNFCMessage
          Disabled -> EnableNFCInstructions
          Enabled -> SearchingUiState
        }
      )
    }

    var fwupProgress by remember { mutableStateOf(0.0f) }

    return when (val state = uiState) {
      is InSessionUiState -> {
        NfcTransactionEffect(
          props = props,
          state = state,
          setProgress = { fwupProgress = it },
          setState = { newState ->
            uiState = newState
          }
        )

        when (state) {
          is SearchingUiState -> {
            FwupNfcBodyModel(
              onCancel = props.onBack,
              status = FwupNfcBodyModel.Status.Searching(),
              eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_INITIATE, FWUP)
            ).asFullScreen()
          }

          is UpdatingUiState -> {
            FwupNfcBodyModel(
              onCancel = props.onBack,
              status = FwupNfcBodyModel.Status.InProgress(fwupProgress = fwupProgress),
              eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_UPDATE_IN_PROGRESS_FWUP)
            ).asFullScreen()
          }

          is InSessionUiState.LostConnectionUiState -> {
            FwupNfcBodyModel(
              onCancel = props.onBack,
              status = FwupNfcBodyModel.Status.LostConnection(fwupProgress = fwupProgress),
              eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_DEVICE_LOST_CONNECTION_FWUP)
            ).asFullScreen()
          }

          is SuccessUiState -> {
            LaunchedEffect("fwup-success") {
              firmwareDataService.updateFirmwareVersion(props.firmwareData.fwupData)
              eventTracker.track(Action.ACTION_APP_FWUP_COMPLETE)
              delayer.delay(
                NfcSuccessScreenDuration(
                  devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform,
                  isHardwareFake = props.isHardwareFake
                )
              )
              props.onDone()
            }

            FwupNfcBodyModel(
              onCancel = null,
              status = FwupNfcBodyModel.Status.Success(),
              eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_SUCCESS, FWUP)
            ).asFullScreen()
          }
        }
      }

      is AndroidOnlyUiState -> {
        when (state) {
          is NoNFCMessage ->
            NoNfcMessageModel(onBack = props.onBack)
              .asModalScreen()

          is EnableNFCInstructions -> {
            EnableNfcInstructionsModel(
              onBack = props.onBack,
              onEnableClick = { uiState = NavigateToEnableNFC }
            ).asModalScreen()
          }

          is NavigateToEnableNFC -> {
            enableNfcNavigator.navigateToEnableNfc { uiState = SearchingUiState }
            NoNfcMessageModel(onBack = props.onBack)
              .asModalScreen()
          }
        }
      }
    }
  }

  @Composable
  private fun NfcTransactionEffect(
    props: FwupNfcSessionUiProps,
    state: InSessionUiState,
    // TODO(W-8034): use Progress type.
    setProgress: (progress: Float) -> Unit,
    setState: (FwupNfcSessionUiState) -> Unit,
  ) {
    LaunchedEffect("nfc-transaction") {
      nfcTransactor
        .transact(
          parameters =
            NfcSession.Parameters(
              isHardwareFake = props.isHardwareFake,
              needsAuthentication = true,
              shouldLock = true,
              skipFirmwareTelemetry = true,
              nfcFlowName = "fwup",
              onTagConnected = {
                eventTracker.track(EventTrackerScreenInfo(NFC_DETECTED, FWUP))
                setState(UpdatingUiState)
              },
              onTagDisconnected = {
                if (state !is SuccessUiState) {
                  setState(InSessionUiState.LostConnectionUiState)
                }
              },
              asyncNfcSigning = false // Unused for FWUP
            ),
          transaction = { session, commands ->
            fwupTransaction(
              session = session,
              commands = commands,
              fwupData = props.firmwareData.fwupData,
              updateSequenceId = { sequenceId ->
                log { "Updating sequence ID: $sequenceId" }
                setSequenceId(sequenceId)
                val progress =
                  fwupProgressCalculator.calculateProgress(
                    sequenceId = sequenceId,
                    finalSequenceId = props.firmwareData.fwupData.finalSequenceId()
                  )
                session.message = "${progress.roundToInt()}%"
                setProgress(progress)
              }
            )
          }
        ).onFailure { error ->
          when (error) {
            is NfcException.IOSOnly.UserCancellation -> {
              props.onBack()
            }
            else -> {
              val inProgress = fwupInProgress
              val transactionType = when (inProgress) {
                true -> FwupTransactionType.ResumeFromSequenceId(getSequenceId())
                false -> FwupTransactionType.StartFromBeginning
              }
              props.onError(error, fwupInProgress, transactionType)
            }
          }
        }.onSuccess {
          setState(SuccessUiState)
        }
    }
  }

  @Throws(NfcException::class, CancellationException::class)
  @Suppress("ThrowsCount")
  private suspend fun fwupTransaction(
    session: NfcSession,
    commands: NfcCommands,
    fwupData: FwupData,
    updateSequenceId: suspend (sequenceId: UInt) -> Unit,
  ) {
    commands.getDeviceInfo(session)

    if (!fwupInProgress) {
      // We have to maintain `fwupInProgress` and reset the sequence ID due to some unfortunate
      // side effects with the `fwup_start` command in delta mode. In short: the app can't tell
      // if the firmware update has started on the firmware or not, because there is no NFC command
      // for that. But at the same time, the app must send `fwup_start` if the firmware hasn't
      // begun the FWUP, and it must NOT send `fwup_start` if it has. This is fixable in firmware
      // but the code must be this way for now.
      setSequenceId(0u)

      val didStart =
        commands.fwupStart(
          session = session,
          patchSize =
            when (fwupData.fwupMode) {
              FwupMode.Normal -> null
              FwupMode.Delta -> fwupData.firmware.size.toUInt()
            },
          fwupMode = fwupData.fwupMode
        )

      if (!didStart) {
        throw NfcException.CommandError()
      }

      fwupInProgress = true
    }

    var sequenceId = getSequenceId()

    while (sequenceId <= fwupData.finalSequenceId()) {
      val off = (sequenceId * fwupData.chunkSize).toInt()
      val size = fwupData.chunkSize.toInt().coerceAtMost(fwupData.firmware.size - off)
      val chunk = fwupData.firmware.toByteArray().copyOfRange(off, off + size)

      val didTransfer =
        commands.fwupTransfer(
          session = session,
          sequenceId = sequenceId,
          fwupData = chunk.toUByteList(),
          offset = 0U,
          fwupMode = fwupData.fwupMode
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
        fwupData = fwupData.signature.toUByteList(),
        offset = fwupData.signatureOffset,
        // Delta or not, the last transfer of the signature is always a "normal" transfer.
        fwupMode = FwupMode.Normal
      )

    // Early return if failed to transfer the final transfer
    if (!didTransfer) {
      throw NfcException.CommandError()
    }

    // Finish
    val finishResult =
      commands.fwupFinish(
        session = session,
        appPropertiesOffset = fwupData.appPropertiesOffset,
        signatureOffset = fwupData.signatureOffset,
        fwupMode = fwupData.fwupMode
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

  private suspend fun getSequenceId(): UInt =
    fwupDataDao.getSequenceId().getOrElse {
      log { "Failed to get sequence ID, using 0" }
      0u
    }

  private suspend fun setSequenceId(sequenceId: UInt) {
    fwupDataDao.setSequenceId(sequenceId)
  }
}

private sealed interface FwupNfcSessionUiState {
  sealed interface InSessionUiState : FwupNfcSessionUiState {
    data object SearchingUiState : InSessionUiState

    data object UpdatingUiState : InSessionUiState

    data object LostConnectionUiState : InSessionUiState

    data object SuccessUiState : InSessionUiState
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
