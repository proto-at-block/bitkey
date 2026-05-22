package build.wallet.statemachine.nfc

import androidx.compose.runtime.*
import bitkey.account.AccountConfig
import bitkey.account.AccountConfigService
import bitkey.account.DefaultAccountConfig
import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.EmulatedPromptOption
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.NfcProgressCallback
import build.wallet.nfc.platform.detectedDeviceInfo
import build.wallet.nfc.platform.toSessionFn
import build.wallet.nfc.transaction.NfcTransaction
import build.wallet.statemachine.core.*
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiState.*
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.Required
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationContent
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiProps
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiStateMachine
import kotlinx.coroutines.launch

/**
 * Result to return from [NfcConfirmableSessionUIStateMachineProps.onRequiresConfirmation] to
 * override the default confirmation handling behavior.
 */
sealed interface ConfirmationHandlerOverride<out T> {
  /**
   * Complete immediately with the given result, skipping the second NFC tap.
   */
  data class CompleteImmediately<T>(val result: T) : ConfirmationHandlerOverride<T>
}

/**
 * Content configuration for pending/denied confirmation result screens.
 *
 * @param pendingHeadline Headline shown when user tapped before deciding on device
 * @param pendingSubline Subline shown when user tapped before deciding on device
 * @param deniedHeadline Headline shown when user denied on device
 */
data class ConfirmationResultContent(
  val pendingHeadline: String = "Review action on Bitkey",
  val pendingSubline: String = "You’ll need to approve or deny on your Bitkey device before tapping again.",
  val deniedHeadline: String = "The action was not confirmed on your Bitkey",
)

/**
 * Props for NFC sessions that return [HardwareInteraction].
 * The [onSuccess] callback receives the unwrapped result type [T], not [HardwareInteraction].
 */
class NfcConfirmableSessionUIStateMachineProps<T>(
  /**
   * The NFC session callback that returns [HardwareInteraction].
   * The [HardwareInteraction] will be automatically unwrapped internally.
   */
  val session: suspend (NfcSession, NfcCommands) -> HardwareInteraction<T>,
  /**
   * Called with the unwrapped result when the interaction completes.
   */
  val onSuccess: suspend (T) -> Unit,
  val config: NfcSessionConfig,
  /**
   * Content to display on the hardware confirmation screen when the interaction
   * requires user confirmation (two-tap flow). Defaults to transaction signing copy.
   */
  val confirmationContent: HardwareConfirmationContent = HardwareConfirmationContent.SignTransaction,
  /**
   * Optional callback to override the default confirmation handling.
   * Return [ConfirmationHandlerOverride.CompleteImmediately] to skip the second NFC tap.
   * Return null to use default behavior (show confirmation UI, wait for second tap).
   */
  val onRequiresConfirmation: (
    (HardwareInteraction.RequiresConfirmation<T>) -> ConfirmationHandlerOverride<T>?
  )? = null,
  /**
   * Optional callback when an emulated prompt option is selected (fake hardware only).
   * Return [ConfirmationHandlerOverride.CompleteImmediately] to complete immediately.
   * Return null to continue with normal confirmation flow.
   *
   * This is called after [EmulatedPromptOption.onSelect] but before transitioning
   * to the confirmation screen.
   *
   * @param isApprove true if the user selected approve, false if deny
   * @param option the selected [EmulatedPromptOption]
   */
  val onEmulatedPromptSelected: (
    (Boolean, EmulatedPromptOption<T>) -> ConfirmationHandlerOverride<T>?
  )? = null,
  /**
   * Content for confirmation result screens (pending/denied).
   * Override to provide operation-specific messaging.
   */
  val confirmationResultContent: ConfirmationResultContent = ConfirmationResultContent(),
) {
  val onConnected: () -> Unit get() = config.onConnected
  val onCancel: () -> Unit get() = config.onCancel
  val onBack: () -> Unit get() = config.onBack
  val onInauthenticHardware: (Throwable) -> Unit get() = config.onInauthenticHardware
  val onError: (NfcException) -> Boolean get() = config.onError
  val needsAuthentication: Boolean get() = config.needsAuthentication
  val hardwareVerification: NfcSessionUIStateMachineProps.HardwareVerification get() = config.hardwareVerification
  val shouldLock: Boolean get() = config.shouldLock
  val segment: AppSegment? get() = config.segment
  val actionDescription: String? get() = config.actionDescription
  val screenPresentationStyle: ScreenPresentationStyle get() = config.screenPresentationStyle
  val eventTrackerContext: NfcEventTrackerScreenIdContext get() = config.eventTrackerContext
  val shouldShowLongRunningOperation: Boolean get() = config.shouldShowLongRunningOperation

  /**
   * Backward-compatible constructor that maintains existing callsite signatures.
   */
  constructor(
    session: suspend (NfcSession, NfcCommands) -> HardwareInteraction<T>,
    onConnected: () -> Unit = {},
    onSuccess: suspend (T) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit = onCancel,
    onInauthenticHardware: (Throwable) -> Unit = {},
    onError: (NfcException) -> Boolean = { false },
    needsAuthentication: Boolean = true,
    hardwareVerification: NfcSessionUIStateMachineProps.HardwareVerification = Required(),
    shouldLock: Boolean = true,
    segment: AppSegment? = null,
    actionDescription: String? = null,
    screenPresentationStyle: ScreenPresentationStyle,
    eventTrackerContext: NfcEventTrackerScreenIdContext,
    shouldShowLongRunningOperation: Boolean = false,
    showNativeSheetOnIos: Boolean = true,
    confirmationContent: HardwareConfirmationContent = HardwareConfirmationContent.SignTransaction,
    onRequiresConfirmation: (
      (HardwareInteraction.RequiresConfirmation<T>) -> ConfirmationHandlerOverride<T>?
    )? = null,
    onEmulatedPromptSelected: (
      (Boolean, EmulatedPromptOption<T>) -> ConfirmationHandlerOverride<T>?
    )? = null,
    confirmationResultContent: ConfirmationResultContent = ConfirmationResultContent(),
    hardwareTypeOverride: HardwareType? = null,
    showDeviceConfirmation: Boolean = false,
    skipFirmwareTelemetry: Boolean = false,
  ) : this(
    session = session,
    onSuccess = onSuccess,
    config = NfcSessionConfig(
      onConnected = onConnected,
      onCancel = onCancel,
      onBack = onBack,
      onInauthenticHardware = onInauthenticHardware,
      onError = onError,
      needsAuthentication = needsAuthentication,
      hardwareVerification = hardwareVerification,
      shouldLock = shouldLock,
      segment = segment,
      actionDescription = actionDescription,
      screenPresentationStyle = screenPresentationStyle,
      eventTrackerContext = eventTrackerContext,
      shouldShowLongRunningOperation = shouldShowLongRunningOperation,
      showNativeSheetOnIos = showNativeSheetOnIos,
      hardwareTypeOverride = hardwareTypeOverride,
      showDeviceConfirmation = showDeviceConfirmation,
      skipFirmwareTelemetry = skipFirmwareTelemetry
    ),
    confirmationContent = confirmationContent,
    onRequiresConfirmation = onRequiresConfirmation,
    onEmulatedPromptSelected = onEmulatedPromptSelected,
    confirmationResultContent = confirmationResultContent
  )

  constructor(
    transaction: NfcTransaction<T>,
    screenPresentationStyle: ScreenPresentationStyle,
    eventTrackerContext: NfcEventTrackerScreenIdContext,
    segment: AppSegment? = null,
    actionDescription: String? = null,
    hardwareVerification: NfcSessionUIStateMachineProps.HardwareVerification,
    onInauthenticHardware: (Throwable) -> Unit = {},
    onError: (NfcException) -> Boolean = { false },
  ) : this(
    session = { session, commands ->
      HardwareInteraction.Completed(transaction.session(session, commands))
    },
    onSuccess = transaction::onSuccess,
    config = NfcSessionConfig(
      onCancel = transaction::onCancel,
      needsAuthentication = transaction.needsAuthentication,
      hardwareVerification = hardwareVerification,
      shouldLock = transaction.shouldLock,
      segment = segment,
      actionDescription = actionDescription,
      screenPresentationStyle = screenPresentationStyle,
      eventTrackerContext = eventTrackerContext,
      onInauthenticHardware = onInauthenticHardware,
      onError = onError
    )
  )
}

interface NfcConfirmableSessionUiStateMachine :
  StateMachine<NfcConfirmableSessionUIStateMachineProps<*>, ScreenModel>

/** Maximum post-confirmation transfer iterations before failing as a safety guard. */
private const val MAX_TRANSFER_ITERATIONS = 5

/**
 * Implementation that delegates to [NfcSessionUIStateMachine] for NFC mechanics
 * and handles [HardwareInteraction] unwrapping internally.
 */
@BitkeyInject(ActivityScope::class)
class NfcConfirmableSessionUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val hardwareConfirmationUiStateMachine: HardwareConfirmationUiStateMachine,
  private val accountConfigService: AccountConfigService,
) : NfcConfirmableSessionUiStateMachine {
  @Composable
  override fun model(props: NfcConfirmableSessionUIStateMachineProps<*>): ScreenModel {
    @Suppress("UNCHECKED_CAST")
    return modelInternal(props as NfcConfirmableSessionUIStateMachineProps<Any?>)
  }

  private fun isHardwareFake(accountConfig: AccountConfig): Boolean =
    when (accountConfig) {
      is FullAccountConfig -> accountConfig.isHardwareFake
      is DefaultAccountConfig -> accountConfig.isHardwareFake
      else -> false
    }

  @Composable
  private fun <T> modelInternal(props: NfcConfirmableSessionUIStateMachineProps<T>): ScreenModel {
    var uiState: NfcConfirmableSessionUiState<T> by remember {
      mutableStateOf(InNfcSession(null))
    }
    // Hoisted to parent scope so it survives across state transitions (InNfcSession →
    // AwaitingConfirmation → InNfcSession). A branch-local remember would be disposed
    // when the composable leaves composition during the confirmation UI hop.
    var capturedDeviceInfo by remember { mutableStateOf<FirmwareDeviceInfo?>(null) }

    return when (val currentState = uiState) {
      is InNfcSession -> inNfcSessionModel(
        currentState = currentState,
        props = props,
        onStateChange = { uiState = it },
        capturedDeviceInfo = capturedDeviceInfo,
        onDeviceInfoCaptured = { capturedDeviceInfo = it }
      )
      is AwaitingConfirmation -> awaitingConfirmationModel(
        currentState = currentState,
        props = props,
        onStateChange = { uiState = it }
      )
      is ShowingConfirmationPending -> confirmationPendingModel(
        currentState = currentState,
        props = props,
        onStateChange = { uiState = it }
      )
      is ShowingConfirmationDenied -> confirmationDeniedModel(
        props = props,
        onStateChange = { uiState = it }
      )
    }
  }

  @Composable
  private fun <T> inNfcSessionModel(
    currentState: InNfcSession<T>,
    props: NfcConfirmableSessionUIStateMachineProps<T>,
    onStateChange: (NfcConfirmableSessionUiState<T>) -> Unit,
    capturedDeviceInfo: FirmwareDeviceInfo?,
    onDeviceInfoCaptured: (FirmwareDeviceInfo) -> Unit,
  ): ScreenModel {
    val continuation = currentState.fetchResult
    // Wrap the session lambda to resolve any RequiresTransfer within the active
    // NFC session. Post-confirmation transfers (e.g., lost app recovery descriptor
    // decryption) must complete before the session ends because the NFC connection
    // is needed to communicate with the hardware. No UI progress is needed here.
    val sessionFn: suspend (NfcSession, NfcCommands) -> HardwareInteraction<T> =
      continuation ?: props.session
    val resolvedSessionFn: suspend (NfcSession, NfcCommands) -> HardwareInteraction<T> =
      { session, commands ->
        var interaction = sessionFn(session, commands)
        var transferCount = 0
        while (interaction is HardwareInteraction.RequiresTransfer) {
          check(++transferCount <= MAX_TRANSFER_ITERATIONS) {
            "Too many RequiresTransfer iterations in confirmation flow"
          }
          interaction = interaction.transferAndFetch(session, commands, NfcProgressCallback {})
        }
        // Capture device info from first tap for second-tap reuse
        if (continuation == null) {
          onDeviceInfoCaptured(commands.detectedDeviceInfo(session))
        }
        interaction
      }
    val wrappedConfig = props.config.copy(
      onError = { exception ->
        handleNfcError(exception, continuation, props, onStateChange)
      },
      // Only show the device confirmation screen on the second tap (when fetching the
      // confirmation result). On the first tap the device is entering confirmation-pending
      // state and hasn't been approved yet.
      showDeviceConfirmation = if (continuation != null) props.config.showDeviceConfirmation else false,
      // Thread captured device info so the second tap reuses the resolved identity
      // instead of probing with a redundant getDeviceInfo() call.
      resolvedDeviceInfoOverride = continuation?.let { capturedDeviceInfo }
    )
    val scope = rememberStableCoroutineScope()
    val nfcModel = nfcSessionUIStateMachine.model(
      props = NfcSessionUIStateMachineProps(
        session = resolvedSessionFn,
        onSuccess = { result ->
          handleHardwareInteractionResult(
            result = result,
            onSuccess = { props.onSuccess(it) },
            onStateChange = onStateChange,
            onRequiresConfirmation = props.onRequiresConfirmation
          )
        },
        config = wrappedConfig
      )
    )
    val emulatedPrompt = currentState.emulatedPrompt
    return if (emulatedPrompt != null) {
      nfcModel.copy(
        bottomSheetModel = SheetModel(
          onClosed = { props.onCancel() },
          body = PromptSelectionFormBodyModel(
            details = emulatedPrompt.details,
            onApprove = {
              scope.launch {
                emulatedPrompt.approve.onSelect?.invoke()
                when (val override = props.onEmulatedPromptSelected?.invoke(true, emulatedPrompt.approve)) {
                  is ConfirmationHandlerOverride.CompleteImmediately -> {
                    props.onSuccess(override.result)
                  }
                  null -> {
                    onStateChange(AwaitingConfirmation(fetchResult = emulatedPrompt.approve.fetchResult))
                  }
                }
              }
            },
            onDeny = {
              scope.launch {
                emulatedPrompt.deny.onSelect?.invoke()
                when (val override = props.onEmulatedPromptSelected?.invoke(false, emulatedPrompt.deny)) {
                  is ConfirmationHandlerOverride.CompleteImmediately -> {
                    props.onSuccess(override.result)
                  }
                  null -> {
                    onStateChange(AwaitingConfirmation(fetchResult = emulatedPrompt.deny.fetchResult))
                  }
                }
              }
            },
            onBack = { props.onCancel() },
            eventTrackerContext = props.eventTrackerContext
          )
        )
      )
    } else {
      nfcModel
    }
  }

  private fun <T> handleNfcError(
    exception: NfcException,
    continuation: (suspend (NfcSession, NfcCommands) -> HardwareInteraction<T>)?,
    props: NfcConfirmableSessionUIStateMachineProps<T>,
    onStateChange: (NfcConfirmableSessionUiState<T>) -> Unit,
  ): Boolean =
    when (exception) {
      is NfcException.ConfirmationPending -> handleConfirmationException(
        continuation = continuation,
        newState = ShowingConfirmationPending(fetchResult = continuation),
        fallbackError = props.onError,
        exception = exception,
        onStateChange = onStateChange
      )
      is NfcException.UserDenied,
      is NfcException.ConfirmationNotCompleted -> handleConfirmationException(
        continuation = continuation,
        newState = ShowingConfirmationDenied(fetchResult = continuation),
        fallbackError = props.onError,
        exception = exception,
        onStateChange = onStateChange
      )
      else -> props.onError(exception)
    }

  private fun <T> handleConfirmationException(
    continuation: (suspend (NfcSession, NfcCommands) -> HardwareInteraction<T>)?,
    newState: NfcConfirmableSessionUiState<T>,
    fallbackError: (NfcException) -> Boolean,
    exception: NfcException,
    onStateChange: (NfcConfirmableSessionUiState<T>) -> Unit,
  ): Boolean =
    if (continuation != null) {
      onStateChange(newState)
      true // handled
    } else {
      // Edge case: no continuation available, propagate error
      fallbackError(exception)
    }

  @Composable
  private fun <T> awaitingConfirmationModel(
    currentState: AwaitingConfirmation<T>,
    props: NfcConfirmableSessionUIStateMachineProps<T>,
    onStateChange: (NfcConfirmableSessionUiState<T>) -> Unit,
  ): ScreenModel {
    val accountConfig = remember { accountConfigService.activeOrDefaultConfig().value }
    return hardwareConfirmationUiStateMachine.model(
      props = HardwareConfirmationUiProps(
        onBack = props.onBack,
        onCancel = props.onCancel,
        onConfirm = {
          onStateChange(InNfcSession(fetchResult = currentState.fetchResult))
        },
        content = props.confirmationContent,
        isHardwareFake = isHardwareFake(accountConfig)
      )
    )
  }

  @Composable
  private fun <T> confirmationPendingModel(
    currentState: ShowingConfirmationPending<T>,
    props: NfcConfirmableSessionUIStateMachineProps<T>,
    onStateChange: (NfcConfirmableSessionUiState<T>) -> Unit,
  ): ScreenModel =
    HardwareConfirmationResultBodyModel(
      headline = props.confirmationResultContent.pendingHeadline,
      subline = props.confirmationResultContent.pendingSubline,
      buttonText = "Got it",
      onAcknowledge = {
        // Return to awaiting confirmation to prompt user to tap again
        onStateChange(
          currentState.fetchResult?.let { AwaitingConfirmation(fetchResult = it) }
            ?: InNfcSession(null)
        )
      },
      eventTrackerScreenId = NfcEventTrackerScreenId.NFC_CONFIRMATION_PENDING
    ).asScreen(props.screenPresentationStyle)

  @Composable
  private fun <T> confirmationDeniedModel(
    props: NfcConfirmableSessionUIStateMachineProps<T>,
    onStateChange: (NfcConfirmableSessionUiState<T>) -> Unit,
  ): ScreenModel =
    HardwareConfirmationResultBodyModel(
      headline = props.confirmationResultContent.deniedHeadline,
      subline = "",
      buttonText = "OK",
      onAcknowledge = {
        // Return to beginning of flow (before first tap)
        onStateChange(InNfcSession(null))
      },
      eventTrackerScreenId = NfcEventTrackerScreenId.NFC_CONFIRMATION_DENIED
    ).asScreen(props.screenPresentationStyle)

  private suspend fun <T> handleHardwareInteractionResult(
    result: HardwareInteraction<T>,
    onSuccess: suspend (T) -> Unit,
    onStateChange: (NfcConfirmableSessionUiState<T>) -> Unit,
    onRequiresConfirmation: (
      (HardwareInteraction.RequiresConfirmation<T>) -> ConfirmationHandlerOverride<T>?
    )?,
  ) {
    when (result) {
      is HardwareInteraction.Completed -> {
        onSuccess(result.result)
      }
      is HardwareInteraction.RequiresConfirmation -> {
        val customResult = onRequiresConfirmation?.invoke(result)
        when (customResult) {
          is ConfirmationHandlerOverride.CompleteImmediately -> {
            onSuccess(customResult.result)
          }
          null -> {
            onStateChange(AwaitingConfirmation(fetchResult = result.toSessionFn()))
          }
        }
      }
      is HardwareInteraction.ConfirmWithEmulatedPrompt -> {
        onStateChange(InNfcSession(emulatedPrompt = result))
      }
      is HardwareInteraction.RequiresTransfer -> {
        // RequiresTransfer is resolved within the NFC session by inNfcSessionModel's
        // wrapper. If we get here, the wrapper was bypassed — this is a programming error.
        throw NfcException.CommandError(
          message = "RequiresTransfer not supported by NfcConfirmableSessionUiStateMachine"
        )
      }
    }
  }
}

/**
 * Internal state for [NfcConfirmableSessionUiStateMachineImpl].
 */
private sealed class NfcConfirmableSessionUiState<T> {
  /**
   * Delegating to [NfcSessionUIStateMachine].
   *
   * @param fetchResult the callback from [HardwareInteraction.RequiresConfirmation] or
   * [EmulatedPromptOption], if set. Used in lieu of [NfcConfirmableSessionUIStateMachineProps.session].
   */
  data class InNfcSession<T>(
    val fetchResult: (suspend (NfcSession, NfcCommands) -> HardwareInteraction<T>)? = null,
    val emulatedPrompt: HardwareInteraction.ConfirmWithEmulatedPrompt<T>? = null,
  ) : NfcConfirmableSessionUiState<T>()

  /**
   * The NFC state machine returned [HardwareInteraction.RequiresConfirmation], and we need to display
   * a confirmation message to the user before an additional NFC tap.
   *
   * @param fetchResult the callback returned with [HardwareInteraction.RequiresConfirmation]
   */
  data class AwaitingConfirmation<T>(
    val fetchResult: suspend (NfcSession, NfcCommands) -> HardwareInteraction<T>,
  ) : NfcConfirmableSessionUiState<T>()

  /**
   * The firmware returned [NfcException.ConfirmationPending] - user tapped before deciding
   * on the device. Shows a screen prompting them to approve/deny first.
   *
   * @param fetchResult the continuation to retry after acknowledgment
   */
  data class ShowingConfirmationPending<T>(
    val fetchResult: (suspend (NfcSession, NfcCommands) -> HardwareInteraction<T>)? = null,
  ) : NfcConfirmableSessionUiState<T>()

  /**
   * The firmware returned [NfcException.UserDenied] - user explicitly denied on the device.
   * Shows a screen acknowledging the denial and allowing retry.
   *
   * @param fetchResult the continuation to retry after acknowledgment
   */
  data class ShowingConfirmationDenied<T>(
    val fetchResult: (suspend (NfcSession, NfcCommands) -> HardwareInteraction<T>)? = null,
  ) : NfcConfirmableSessionUiState<T>()
}
