package build.wallet.statemachine.send.signtransaction

import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.SIGN_TRANSACTION
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.nfc.NfcException
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine managing the NFC session for transaction signing and vending UI screen models
 * for different session states.
 *
 * Handles both W1 (single-tap) and W3 (two-tap with chunked transfer) flows via response-based
 * routing using [build.wallet.nfc.platform.HardwareInteraction] types.
 */
interface SignTransactionNfcSessionUiStateMachine :
  StateMachine<SignTransactionNfcSessionUiProps, ScreenModel>

/**
 * @property psbt: The PSBT to sign with the hardware device.
 * @property onBack: Callback for exiting the NFC session without completion (i.e. user taps 'Cancel').
 * @property onSuccess: Callback invoked with the signed PSBT after successful hardware signing.
 * @property onError: Error callback for handling NFC and hardware errors during signing.
 * Return `true` if the error was handled, `false` to show the default NFC error UI.
 * @property eventTrackerContext: Context for analytics tracking. Defaults to [SIGN_TRANSACTION].
 * Use a more specific context (e.g., [NfcEventTrackerScreenIdContext.UTXO_CONSOLIDATION_SIGN_TRANSACTION])
 * when appropriate for better analytics attribution.
 */
data class SignTransactionNfcSessionUiProps(
  val psbt: Psbt,
  val onBack: () -> Unit,
  val onSuccess: (Psbt) -> Unit,
  val onError: (NfcException) -> Boolean = { false },
  val eventTrackerContext: NfcEventTrackerScreenIdContext = SIGN_TRANSACTION,
)
