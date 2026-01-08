package build.wallet.statemachine.nfc

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.compose.collections.buildImmutableList
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.PromptOption
import build.wallet.nfc.transaction.NfcTransaction
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.nfc.NfcContinuationSessionUiState.*
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.Required
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiProps
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiStateMachine
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

class NfcContinuationSessionUIStateMachineProps<T>(
  /**
   * The NFC session callback that callers should use to perform commands.
   * Callers should return the action that should be taken upon a successful transaction.
   */
  val session: suspend (NfcSession, NfcCommands) -> HardwareInteraction<T>,
  val onSuccess: suspend (T) -> Unit,
  val config: NfcSessionConfig,
) {
  // Backward compatibility: delegate to config properties
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
    )
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

interface NfcContinuationSessionUiStateMachine :
  StateMachine<NfcContinuationSessionUIStateMachineProps<*>, ScreenModel>

@BitkeyInject(ActivityScope::class)
class NfcContinuationSessionUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val hardwareConfirmationUiStateMachine: HardwareConfirmationUiStateMachine,
) : NfcContinuationSessionUiStateMachine {
  @Composable
  override fun model(props: NfcContinuationSessionUIStateMachineProps<*>): ScreenModel {
    var uiState: NfcContinuationSessionUiState by remember {
      mutableStateOf(InNfcSession(null))
    }

    return when (uiState) {
      is InNfcSession -> {
        val continuation = uiState.tryContinue
        nfcSessionUIStateMachine.model(
          props = NfcSessionUIStateMachineProps(
            session = if (continuation != null) {
              { session, _ -> continuation.invoke(session) }
            } else {
              props.session
            },
            onSuccess = { result ->
              @Suppress("UNCHECKED_CAST")
              handleHardwareInteractionResult(
                result = result,
                onSuccess = props.onSuccess as suspend (Any?) -> Unit,
                onStateChange = { newState -> uiState = newState }
              )
            },
            config = props.config
          )
        )
      }
      is InteractingWithApp -> hardwareConfirmationUiStateMachine.model(
        props = HardwareConfirmationUiProps(
          onBack = props.onCancel,
          onConfirm = {
            uiState = InNfcSession(
              tryContinue = uiState.tryContinue
            )
          }
        )
      )
      is EmulatingPrompt<*> -> {
        val currentState = uiState as EmulatingPrompt<*>

        PromptSelectionFormBodyModel(
          options = currentState.options.map { it.name },
          onOptionSelected = { selectedIndex ->
            val selectedOption = currentState.options[selectedIndex]
            uiState = InNfcSession(tryContinue = selectedOption.onSelect)
          },
          onBack = props.onCancel,
          eventTrackerContext = props.eventTrackerContext
        ).asScreen(props.screenPresentationStyle)
      }
    }
  }

  private suspend fun handleHardwareInteractionResult(
    result: HardwareInteraction<*>,
    onSuccess: suspend (Any?) -> Unit,
    onStateChange: (NfcContinuationSessionUiState) -> Unit,
  ) {
    when (result) {
      is HardwareInteraction.Completed<*> -> {
        onSuccess(result.result)
      }
      is HardwareInteraction.Continuation<*> -> {
        onStateChange(
          InteractingWithApp(
            tryContinue = result.tryContinue
          )
        )
      }
      is HardwareInteraction.EmulatePrompt<*> -> {
        onStateChange(
          EmulatingPrompt(options = result.options)
        )
      }
    }
  }
}

/**
 * Form body model for displaying a list of selectable prompt options.
 */
private data class PromptSelectionFormBodyModel(
  val options: List<String>,
  val onOptionSelected: (Int) -> Unit,
  override val onBack: () -> Unit,
  override val eventTrackerContext: NfcEventTrackerScreenIdContext,
) : FormBodyModel(
    id = NfcEventTrackerScreenId.NFC_INITIATE,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onBack)),
    header = FormHeaderModel(
      headline = "Select Option",
      subline = "Choose the W3 response you would like to simulate."
    ),
    mainContentList = buildImmutableList {
      add(
        FormMainContentModel.ListGroup(
          listGroupModel = ListGroupModel(
            items = buildImmutableList {
              options.forEachIndexed { index, optionName ->
                add(
                  ListItemModel(
                    title = optionName,
                    onClick = { onOptionSelected(index) }
                  )
                )
              }
            },
            style = ListGroupStyle.CARD_ITEM
          )
        )
      )
    },
    primaryButton = null,
    eventTrackerContext = eventTrackerContext
  )

private sealed class NfcContinuationSessionUiState {
  abstract val tryContinue: (suspend (NfcSession) -> HardwareInteraction<*>)?

  /**
   * Delegating to [NfcSessionUIStateMachine].
   *
   * @param tryContinue the callback returned with the last instance of
   * [HardwareInteraction.Continuation], if one was ever emitted. Used in lieu of the
   * [NfcContinuationSessionUIStateMachineProps#session], if it exists.
   */
  data class InNfcSession(
    override val tryContinue: (suspend (NfcSession) -> HardwareInteraction<*>)? = null,
  ) : NfcContinuationSessionUiState()

  /**
   * The NFC state machine returned [HardwareInteraction.Continuation], and we need to display
   * some confirmation message to the user before an additional nfc tap.
   *
   * @param tryContinue the callback returned with [HardwareInteraction.Continuation]
   */
  data class InteractingWithApp(
    override val tryContinue: (suspend (NfcSession) -> HardwareInteraction<*>),
  ) : NfcContinuationSessionUiState()

  /**
   * The NFC state machine returned [HardwareInteraction.EmulatePrompt] (only returned from
   * fake [NfcCommands] instances). Display the [options] to the users
   *
   * @param options the list of possible user responses returned with
   * [HardwareInteraction.EmulatePrompt].
   */
  data class EmulatingPrompt<T>(
    val options: List<PromptOption<T>>,
  ) : NfcContinuationSessionUiState() {
    override val tryContinue: (suspend (NfcSession) -> HardwareInteraction<*>)? = null
  }
}
