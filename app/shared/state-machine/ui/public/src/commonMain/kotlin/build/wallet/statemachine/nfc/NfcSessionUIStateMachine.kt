package build.wallet.statemachine.nfc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId.NFC_INITIATE
import build.wallet.nfc.NfcAvailability.Available.Disabled
import build.wallet.nfc.NfcAvailability.Available.Enabled
import build.wallet.nfc.NfcAvailability.NotAvailable
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcReaderCapabilityProvider
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcTransactor
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.transaction.NfcTransaction
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.NfcErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.nfc.NfcSessionUIState.AndroidOnly
import build.wallet.statemachine.nfc.NfcSessionUIState.AndroidOnly.EnableNFCInstructions
import build.wallet.statemachine.nfc.NfcSessionUIState.AndroidOnly.NavigateToEnableNFC
import build.wallet.statemachine.nfc.NfcSessionUIState.AndroidOnly.NoNFCMessage
import build.wallet.statemachine.nfc.NfcSessionUIState.Error
import build.wallet.statemachine.nfc.NfcSessionUIState.InSession
import build.wallet.statemachine.nfc.NfcSessionUIState.InSession.Communicating
import build.wallet.statemachine.nfc.NfcSessionUIState.InSession.Searching
import build.wallet.statemachine.nfc.NfcSessionUIState.InSession.Success
import build.wallet.statemachine.platform.nfc.EnableNfcNavigator
import build.wallet.time.Delayer
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import build.wallet.statemachine.nfc.NfcBodyModel.Status.Connected as ConnectedState
import build.wallet.statemachine.nfc.NfcBodyModel.Status.Searching as SearchingState
import build.wallet.statemachine.nfc.NfcBodyModel.Status.Success as SuccessState

class NfcSessionUIStateMachineProps<T>(
  /**
   * The NFC session callback that callers should use to perform commands.
   * Callers should return the action that should be taken upon a successful transaction.
   */
  val session: suspend (NfcSession, NfcCommands) -> T,
  val onConnected: () -> Unit = {},
  val onDisconnected: () -> Unit = {},
  val onSuccess: suspend (@UnsafeVariance T) -> Unit,
  val onCancel: () -> Unit,
  val onInauthenticHardware: () -> Unit = {},
  val isHardwareFake: Boolean,
  val needsAuthentication: Boolean = true,
  val shouldLock: Boolean = true,
  // TODO(BKR-1117): make non-nullable
  val segment: AppSegment? = null,
  val actionDescription: String? = null,
  val screenPresentationStyle: ScreenPresentationStyle,
  val eventTrackerContext: NfcEventTrackerScreenIdContext,
) {
  constructor(
    transaction: NfcTransaction<T>,
    screenPresentationStyle: ScreenPresentationStyle,
    eventTrackerContext: NfcEventTrackerScreenIdContext,
    segment: AppSegment? = null,
    actionDescription: String? = null,
    onInauthenticHardware: () -> Unit = {},
  ) : this(
    session = transaction::session,
    onSuccess = transaction::onSuccess,
    onCancel = transaction::onCancel,
    isHardwareFake = transaction.isHardwareFake,
    needsAuthentication = transaction.needsAuthentication,
    shouldLock = transaction.shouldLock,
    segment = segment,
    actionDescription = actionDescription,
    screenPresentationStyle = screenPresentationStyle,
    eventTrackerContext = eventTrackerContext,
    onInauthenticHardware = onInauthenticHardware
  )
}

interface NfcSessionUIStateMachine : StateMachine<NfcSessionUIStateMachineProps<*>, ScreenModel>

class NfcSessionUIStateMachineImpl(
  private val delayer: Delayer,
  private val nfcReaderCapabilityProvider: NfcReaderCapabilityProvider,
  private val enableNfcNavigator: EnableNfcNavigator,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val nfcTransactor: NfcTransactor,
) : NfcSessionUIStateMachine {
  @Composable
  override fun model(props: NfcSessionUIStateMachineProps<*>): ScreenModel {
    var newState by remember {
      mutableStateOf(
        when (nfcReaderCapabilityProvider.get(props.isHardwareFake).availability()) {
          NotAvailable -> NoNFCMessage
          Disabled -> EnableNFCInstructions
          Enabled -> Searching
        }
      )
    }

    if (newState is InSession) {
      LaunchedEffect("nfc-transaction") {
        nfcTransactor.transact(
          parameters =
            NfcSession.Parameters(
              isHardwareFake = props.isHardwareFake,
              needsAuthentication = props.needsAuthentication,
              shouldLock = props.shouldLock,
              skipFirmwareTelemetry = false, // Only true for FWUP.
              onTagConnected = { props.onConnected().also { newState = Communicating } },
              onTagDisconnected = {
                props.onDisconnected().also { if (newState !is Success) newState = Searching }
              }
            ),
          transaction = props.session
        ).onSuccess {
          newState = Success

          delayer.delay(
            NfcSuccessScreenDuration(
              devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform,
              isHardwareFake = props.isHardwareFake
            )
          )
          props.onSuccess.invoke(it)
        }.onFailure { error ->
          when (error) {
            is NfcException.IOSOnly.UserCancellation ->
              props.onCancel()
            else ->
              newState = Error(error)
          }
        }
      }
    }

    return when (val currentState = newState) {
      is InSession ->
        when (currentState) {
          is Searching -> {
            NfcBodyModel(
              text = "Hold device here behind phone",
              status = SearchingState(props.onCancel),
              eventTrackerScreenInfo =
                EventTrackerScreenInfo(
                  eventTrackerScreenId = NFC_INITIATE,
                  eventTrackerScreenIdContext = props.eventTrackerContext
                )
            ).asFullScreen()
          }

          is Communicating -> {
            NfcBodyModel(
              text = "Connected",
              status = ConnectedState(props.onCancel),
              eventTrackerScreenInfo =
                EventTrackerScreenInfo(
                  eventTrackerScreenId = NfcEventTrackerScreenId.NFC_DETECTED,
                  eventTrackerScreenIdContext = props.eventTrackerContext
                )
            ).asFullScreen()
          }

          is Success -> {
            NfcBodyModel(
              text = "Success",
              status = SuccessState,
              eventTrackerScreenInfo =
                EventTrackerScreenInfo(
                  eventTrackerScreenId = NfcEventTrackerScreenId.NFC_SUCCESS,
                  eventTrackerScreenIdContext = props.eventTrackerContext
                )
            ).asFullScreen()
          }
        }

      is AndroidOnly -> {
        when (currentState) {
          is NoNFCMessage ->
            NoNfcMessageModel(onBack = props.onCancel)
              .asScreen(presentationStyle = props.screenPresentationStyle)

          is EnableNFCInstructions -> {
            EnableNfcInstructionsModel(
              onBack = props.onCancel,
              onEnableClick = { newState = NavigateToEnableNFC }
            ).asScreen(presentationStyle = props.screenPresentationStyle)
          }

          is NavigateToEnableNFC -> {
            enableNfcNavigator.navigateToEnableNfc { newState = Searching }

            NoNfcMessageModel(onBack = props.onCancel)
              .asScreen(presentationStyle = props.screenPresentationStyle)
          }
        }
      }

      is Error -> {
        NfcErrorFormBodyModel(
          exception = currentState.nfcException,
          onPrimaryButtonClick = props.onCancel,
          onSecondaryButtonClick = props.onInauthenticHardware,
          segment = props.segment,
          actionDescription = props.actionDescription,
          eventTrackerScreenId = NfcEventTrackerScreenId.NFC_FAILURE,
          eventTrackerScreenIdContext = props.eventTrackerContext
        ).asScreen(props.screenPresentationStyle)
      }
    }
  }
}

private sealed class NfcSessionUIState {
  sealed class InSession : NfcSessionUIState() {
    data object Searching : InSession()

    data object Communicating : InSession()

    data object Success : InSession()
  }

  sealed class AndroidOnly : NfcSessionUIState() {
    /** Showing a message for mobile devices that don't have NFC -- Android-only. */
    data object NoNFCMessage : AndroidOnly()

    /** Showing a message for when NFC is not enabled -- Android-only. */
    data object EnableNFCInstructions : AndroidOnly()

    /** Navigating to the settings screen for enabling NFC -- Android-only. */
    data object NavigateToEnableNFC : AndroidOnly()
  }

  data class Error(val nfcException: NfcException) : NfcSessionUIState()
}
