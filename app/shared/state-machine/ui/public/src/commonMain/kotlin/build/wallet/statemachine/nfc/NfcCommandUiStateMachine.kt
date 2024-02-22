package build.wallet.statemachine.nfc

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.nfc.NfcCommandData

/**
 * A UI state machine for rendering [NfcCommandData] types.
 */
interface NfcCommandUiStateMachine : StateMachine<NfcUiProps, ScreenModel>

/**
 * @param onCancel a callback for when customer cancels NFC command via UI.
 */
data class NfcUiProps(
  val nfcCommandData: NfcCommandData,
  val onCancel: () -> Unit,
  val isHardwareFake: Boolean,
  val screenPresentationStyle: ScreenPresentationStyle,
)
