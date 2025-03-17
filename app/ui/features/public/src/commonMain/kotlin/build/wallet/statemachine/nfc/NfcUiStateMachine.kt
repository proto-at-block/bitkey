package build.wallet.statemachine.nfc

import androidx.compose.runtime.Composable
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.nfc.NfcManagerError
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine

/** State machine for managing NFC interactions, includes scanning, success, and failure screens. */
@Deprecated("Use NfcCommandDataStateMachine and NfcCommandUiStateMachine")
typealias NfcUiStateMachine<RequestT, ResponseT> =
  StateMachine<NfcUiStateMachineProps<RequestT, ResponseT>, ScreenModel>

/** Properties defining the behavior of an [NfcUiStateMachine]. */
data class NfcUiStateMachineProps<RequestT : Any, ResponseT : Any>(
  val request: RequestT,
  val context: String,
  val onSuccess: (ErrorScreenRedirection, ResponseT) -> Unit,
  val onCancel: () -> Unit,
  val onFailure: (NfcManagerError) -> Unit,
  val screenPresentationStyle: ScreenPresentationStyle,
  val eventTrackerContext: NfcEventTrackerScreenIdContext?,
)

/**
 * Convenience method for wrapping an [NfcUiStateMachine] to hide its implementation details of NFC,
 * including the [RequestT] and [ResponseT] associated with the underlying NfcCommand.
 *
 * @param context Identifier for NFC errors.
 * @param nfcUiStateMachine An implementation of NfcStateMachine to wrap.
 * @param getRequest How to build the [RequestT] from [PropsT]
 * @param getOnSuccess Return callback to execute on success, based on [PropsT] and
 *   receiving [ResponseT]
 * @param getOnFailure Return the callback to execute on failure, receiving [NfcManagerError].
 * @param getOnCancel Return the callback to execute on leaving this state machine.
 * @param getScreenPresentationStyle Return [ScreenPresentationStyle], noting that
 *   NFC scan and success screens will be displayed full screen.
 * @param eventTrackerContext context for screen events emitted by this state machine to
 * disambiguate
 */
@Deprecated("Use NfcCommandDataStateMachine and NfcCommandUiStateMachine")
fun <PropsT : Any, RequestT : Any, ResponseT : Any> nfcStateMachineWrapper(
  context: String,
  nfcUiStateMachine: NfcUiStateMachine<RequestT, ResponseT>,
  getRequest: (PropsT) -> RequestT,
  getOnSuccess: ErrorScreenRedirection.(PropsT, ResponseT) -> (() -> Unit),
  getOnFailure: (NfcManagerError) -> Unit,
  getOnCancel: (PropsT) -> (() -> Unit),
  getScreenPresentationStyle: (PropsT) -> ScreenPresentationStyle,
  eventTrackerContext: NfcEventTrackerScreenIdContext?,
): StateMachine<PropsT, ScreenModel> {
  return object : StateMachine<PropsT, ScreenModel> {
    @Composable
    override fun model(props: PropsT): ScreenModel {
      return nfcUiStateMachine.model(
        props =
          NfcUiStateMachineProps(
            request = getRequest(props),
            context = context,
            onSuccess = { errorScreenRedirection, response ->
              getOnSuccess(errorScreenRedirection, props, response).invoke()
            },
            onFailure = { getOnFailure(it) },
            onCancel = { getOnCancel(props).invoke() },
            screenPresentationStyle = getScreenPresentationStyle(props),
            eventTrackerContext = eventTrackerContext
          )
      )
    }
  }
}

fun interface ErrorScreenRedirection {
  fun redirectToErrorScreen(title: String)
}
