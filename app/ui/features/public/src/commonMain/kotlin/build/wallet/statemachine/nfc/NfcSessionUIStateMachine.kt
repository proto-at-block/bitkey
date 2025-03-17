package build.wallet.statemachine.nfc

import androidx.compose.runtime.*
import bitkey.account.AccountConfigService
import bitkey.account.DefaultAccountConfig
import bitkey.account.FullAccountConfig
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId.NFC_INITIATE
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.AsyncNfcSigningFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.nfc.NfcAvailability.Available.Disabled
import build.wallet.nfc.NfcAvailability.Available.Enabled
import build.wallet.nfc.NfcAvailability.NotAvailable
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcReaderCapability
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcTransactor
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.transaction.NfcTransaction
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.statemachine.core.*
import build.wallet.statemachine.nfc.NfcSessionUIState.*
import build.wallet.statemachine.nfc.NfcSessionUIState.AndroidOnly.*
import build.wallet.statemachine.nfc.NfcSessionUIState.InSession.*
import build.wallet.statemachine.platform.nfc.EnableNfcNavigator
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.delay
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
  val onSuccess: suspend (@UnsafeVariance T) -> Unit,
  val onCancel: () -> Unit,
  val onInauthenticHardware: (Throwable) -> Unit = {},
  val needsAuthentication: Boolean = true,
  val shouldLock: Boolean = true,
  // TODO(BKR-1117): make non-nullable
  val segment: AppSegment? = null,
  val actionDescription: String? = null,
  val screenPresentationStyle: ScreenPresentationStyle,
  val eventTrackerContext: NfcEventTrackerScreenIdContext,
  /**
   *  Used to indicate that an operation may take awhile, by using an indeterminate spinner on Android and
   *  add some flavor text on iOS.
   */
  val shouldShowLongRunningOperation: Boolean = false,
) {
  constructor(
    transaction: NfcTransaction<T>,
    screenPresentationStyle: ScreenPresentationStyle,
    eventTrackerContext: NfcEventTrackerScreenIdContext,
    segment: AppSegment? = null,
    actionDescription: String? = null,
    onInauthenticHardware: (Throwable) -> Unit = {},
  ) : this(
    session = transaction::session,
    onSuccess = transaction::onSuccess,
    onCancel = transaction::onCancel,
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

@BitkeyInject(ActivityScope::class)
class NfcSessionUIStateMachineImpl(
  private val nfcReaderCapability: NfcReaderCapability,
  private val enableNfcNavigator: EnableNfcNavigator,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val nfcTransactor: NfcTransactor,
  private val asyncNfcSigningFeatureFlag: AsyncNfcSigningFeatureFlag,
  private val accountConfigService: AccountConfigService,
) : NfcSessionUIStateMachine {
  /**
   * Text shown under the progress spinner (on Android) or on the iOS NFC Sheet when performing
   * a potentially long running operation, like UTXO Consolidation.
   */
  private val longRunningOperationText = "This can take up to 1 minute…"

  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(props: NfcSessionUIStateMachineProps<*>): ScreenModel {
    val accountConfig = remember { accountConfigService.activeOrDefaultConfig().value }
    val isHardwareFake = remember {
      when (accountConfig) {
        is FullAccountConfig -> accountConfig.isHardwareFake
        is DefaultAccountConfig -> accountConfig.isHardwareFake
        else -> false
      }
    }

    var newState by remember {
      mutableStateOf(
        when (nfcReaderCapability.availability(isHardwareFake)) {
          NotAvailable -> NoNFCMessage
          Disabled -> EnableNFCInstructions
          Enabled -> Searching
        }
      )
    }

    if (newState is InSession) {
      LaunchedEffect("nfc-transaction") {
        nfcTransactor
          .transact(
            parameters =
              NfcSession.Parameters(
                isHardwareFake = isHardwareFake,
                needsAuthentication = props.needsAuthentication,
                shouldLock = props.shouldLock,
                skipFirmwareTelemetry = false, // Only true for FWUP.
                onTagConnected = { session ->
                  props.onConnected()
                  if (props.shouldShowLongRunningOperation) {
                    session?.message = longRunningOperationText
                  }
                  newState = Communicating
                },
                onTagDisconnected = {
                  // NB: This is only called on Android.
                  if (newState !is Success) newState = Searching
                },
                asyncNfcSigning = asyncNfcSigningFeatureFlag.isEnabled(),
                nfcFlowName = props.eventTrackerContext.name
              ),
            transaction = props.session
          ).onSuccess {
            newState = Success

            delay(
              NfcSuccessScreenDuration(
                devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform,
                isHardwareFake = isHardwareFake
              )
            )
            // Must be cast to satisfy compiler type resolution
            @Suppress("USELESS_CAST")
            (props.onSuccess as suspend (Any?) -> Unit).invoke(it)
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
                  eventTrackerContext = props.eventTrackerContext
                )
            ).asPlatformNfcScreen()
          }

          is Communicating -> {
            val text = if (props.shouldShowLongRunningOperation) {
              longRunningOperationText
            } else {
              "Connected"
            }
            NfcBodyModel(
              text = text,
              status = ConnectedState(
                onCancel = props.onCancel,
                showProgressSpinner = props.shouldShowLongRunningOperation
              ),
              eventTrackerScreenInfo =
                EventTrackerScreenInfo(
                  eventTrackerScreenId = NfcEventTrackerScreenId.NFC_DETECTED,
                  eventTrackerContext = props.eventTrackerContext
                )
            ).asPlatformNfcScreen()
          }

          is Success -> {
            NfcBodyModel(
              text = "Success",
              status = SuccessState,
              eventTrackerScreenInfo =
                EventTrackerScreenInfo(
                  eventTrackerScreenId = NfcEventTrackerScreenId.NFC_SUCCESS,
                  eventTrackerContext = props.eventTrackerContext
                )
            ).asPlatformNfcScreen()
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
          onSecondaryButtonClick = {
            props.onInauthenticHardware(currentState.nfcException)
          },
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

  data class Error(
    val nfcException: NfcException,
  ) : NfcSessionUIState()
}
