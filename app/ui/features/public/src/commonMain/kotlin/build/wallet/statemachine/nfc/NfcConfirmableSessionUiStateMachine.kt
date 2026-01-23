package build.wallet.statemachine.nfc

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.EmulatedPromptOption
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.transaction.NfcTransaction
import build.wallet.statemachine.core.*
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiState.*
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.Required
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
   */
  val onEmulatedPromptSelected: (
    (EmulatedPromptOption<T>) -> ConfirmationHandlerOverride<T>?
  )? = null,
) {
  val onConnected: () -> Unit get() = config.onConnected
  val onCancel: () -> Unit get() = config.onCancel
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
    onRequiresConfirmation: (
      (HardwareInteraction.RequiresConfirmation<T>) -> ConfirmationHandlerOverride<T>?
    )? = null,
    onEmulatedPromptSelected: (
      (EmulatedPromptOption<T>) -> ConfirmationHandlerOverride<T>?
    )? = null,
  ) : this(
    session = session,
    onSuccess = onSuccess,
    config = NfcSessionConfig(
      onConnected = onConnected,
      onCancel = onCancel,
      onInauthenticHardware = onInauthenticHardware,
      onError = onError,
      needsAuthentication = needsAuthentication,
      hardwareVerification = hardwareVerification,
      shouldLock = shouldLock,
      segment = segment,
      actionDescription = actionDescription,
      screenPresentationStyle = screenPresentationStyle,
      eventTrackerContext = eventTrackerContext,
      shouldShowLongRunningOperation = shouldShowLongRunningOperation
    ),
    onRequiresConfirmation = onRequiresConfirmation,
    onEmulatedPromptSelected = onEmulatedPromptSelected
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

interface NfcConfirmableSessionUiStateMachine {
  @Composable
  fun <T> model(props: NfcConfirmableSessionUIStateMachineProps<T>): ScreenModel
}

/**
 * Implementation that delegates to [NfcSessionUIStateMachine] for NFC mechanics
 * and handles [HardwareInteraction] unwrapping internally.
 */
@BitkeyInject(ActivityScope::class)
class NfcConfirmableSessionUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val hardwareConfirmationUiStateMachine: HardwareConfirmationUiStateMachine,
) : NfcConfirmableSessionUiStateMachine {
  @Composable
  override fun <T> model(props: NfcConfirmableSessionUIStateMachineProps<T>): ScreenModel {
    var uiState: NfcConfirmableSessionUiState<T> by remember {
      mutableStateOf(InNfcSession(null))
    }

    return when (val currentState = uiState) {
      is InNfcSession -> {
        val continuation = currentState.fetchResult
        nfcSessionUIStateMachine.model(
          props = NfcSessionUIStateMachineProps<HardwareInteraction<T>>(
            session = continuation ?: props.session,
            onSuccess = { result ->
              handleHardwareInteractionResult(
                result = result,
                onSuccess = { props.onSuccess(it) },
                onStateChange = { newState -> uiState = newState },
                onRequiresConfirmation = props.onRequiresConfirmation
              )
            },
            config = props.config
          )
        )
      }

      is AwaitingConfirmation -> {
        hardwareConfirmationUiStateMachine.model(
          props = HardwareConfirmationUiProps(
            onBack = props.onCancel,
            onConfirm = {
              uiState = InNfcSession(fetchResult = currentState.fetchResult)
            }
          )
        )
      }

      is EmulatingPrompt -> {
        val scope = rememberStableCoroutineScope()
        PromptSelectionFormBodyModel(
          options = currentState.options.map { it.name },
          onOptionSelected = { selectedIndex ->
            val selectedOption = currentState.options[selectedIndex]
            scope.launch {
              selectedOption.onSelect?.invoke()
              val override = props.onEmulatedPromptSelected?.invoke(selectedOption)
              when (override) {
                is ConfirmationHandlerOverride.CompleteImmediately -> {
                  props.onSuccess(override.result)
                }
                null -> {
                  uiState = AwaitingConfirmation(fetchResult = selectedOption.fetchResult)
                }
              }
            }
          },
          onBack = props.onCancel,
          eventTrackerContext = props.eventTrackerContext
        ).asScreen(props.screenPresentationStyle)
      }
    }
  }

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
            onStateChange(AwaitingConfirmation(fetchResult = result.fetchResult))
          }
        }
      }
      is HardwareInteraction.ConfirmWithEmulatedPrompt -> {
        onStateChange(EmulatingPrompt(options = result.options))
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
   * The NFC command returned [HardwareInteraction.ConfirmWithEmulatedPrompt] (only from
   * fake [NfcCommands] instances). Display the [options] to simulate device confirmation.
   *
   * After user selection, transitions to [AwaitingConfirmation] with the selected option's
   * [EmulatedPromptOption.fetchResult].
   */
  data class EmulatingPrompt<T>(
    val options: List<EmulatedPromptOption<T>>,
  ) : NfcConfirmableSessionUiState<T>()
}
