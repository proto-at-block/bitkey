package build.wallet.statemachine.nfc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcSessionFake
import build.wallet.nfc.platform.NfcCommands
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import kotlinx.coroutines.launch

/**
 * A fake [NfcSessionUIStateMachine] that can optionally execute the session lambda
 * to enable testing of NFC commands executed within the session.
 *
 * @param id The id included with the [BodyModelMock].
 * @param executeSession Whether to execute the session lambda when model is composed.
 *   When true, you must provide [nfcSession] and [nfcCommands].
 * @param nfcSession The fake NFC session to pass to the session lambda when [executeSession] is true.
 * @param nfcCommands The mock NFC commands to pass to the session lambda when [executeSession] is true.
 */
class NfcSessionUIStateMachineFake(
  private val id: String = "nfc-session",
  private val nfcSession: NfcSession = NfcSessionFake(),
  private val nfcCommands: NfcCommands,
) : NfcSessionUIStateMachine {
  @Composable
  override fun model(props: NfcSessionUIStateMachineProps<*>): ScreenModel {
    val scope = rememberCoroutineScope()

    // Launch the session in the composition scope
    // This executes immediately and synchronously in test dispatcher
    scope.launch {
      try {
        val result = props.session(nfcSession, nfcCommands)
        // Cast to suppress type issues with star projection
        @Suppress("UNCHECKED_CAST")
        (props.onSuccess as suspend (Any?) -> Unit).invoke(result)
      } catch (e: Exception) {
        // If there's an error in the session, the test should handle it
        throw e
      }
    }

    return ScreenModel(
      body = BodyModelMock(
        id = id,
        latestProps = props
      ),
      presentationStyle = ScreenPresentationStyle.Root
    )
  }
}
