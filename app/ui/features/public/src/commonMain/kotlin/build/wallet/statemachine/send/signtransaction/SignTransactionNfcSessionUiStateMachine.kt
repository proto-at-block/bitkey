package build.wallet.statemachine.send.signtransaction

import bitkey.account.HardwareType
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.SIGN_TRANSACTION
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.nfc.NfcException
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.nfc.HardwareConfirmationResultBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationContent

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
 * @property account The full account whose keybox and configuration back this signing flow.
 * @property psbt: The PSBT to sign with the hardware device.
 * @property spendingKeyset: Optional spending keyset to use for signing. When null, the active
 * keyset from the current account is used. This is needed for sweep flows where PSBTs may need
 * to be signed against different (potentially rotated) keysets.
 * @property useRecoveryHwAuthKey: When true, the hardware pairing check uses the hardware auth key
 * from the in-progress recovery (if any) instead of the active keybox's key. This is needed during
 * recovery sweep flows where the active keybox may not yet reflect the new hardware. Defaults to false.
 * @property hardwareTypeOverride: When set, overrides the hardware type derived from the account
 * config. Used during W3 upgrade sweeps where the old W1 hardware must sign transactions even
 * though the account has already been updated to W3.
 * @property skipPairingCheck: When true, skips the hardware pairing verification. This is needed
 * during W3 upgrade sweeps where the old W1 hardware must sign transactions but the active keybox
 * has already been updated to the new W3 hardware, so the pairing check would fail.
 * @property skipFirmwareTelemetry: When true, skips firmware telemetry collection for the NFC
 * session. This is needed during W3 upgrade sweeps where the old W1 hardware is intentionally
 * tapped after pairing the new W3, so telemetry should not overwrite the paired W3 device info.
 * @property onBack: Callback for exiting the NFC session without completion (i.e. user taps 'Cancel').
 * @property onSuccess: Callback invoked with the signed PSBT after successful hardware signing.
 * @property onError: Error callback for handling NFC and hardware errors during signing.
 * Return `true` if the error was handled, `false` to show the default NFC error UI.
 * @property eventTrackerContext: Context for analytics tracking. Defaults to [SIGN_TRANSACTION].
 * Use a more specific context (e.g., [NfcEventTrackerScreenIdContext.UTXO_CONSOLIDATION_SIGN_TRANSACTION])
 * when appropriate for better analytics attribution.
 * @property showNativeSheetOnIos: When true, iOS keeps the previous screen visible underneath
 * the native CoreNFC sheet instead of rendering the custom Bitkey NFC background. Disable this
 * for longer-running flows such as sweeps and UTXO consolidation.
 * @property confirmationContent: Content configuration for the hardware confirmation screen
 * shown during the W3 two-tap flow. Different callers can provide operation-specific copy
 * (e.g. "Send transaction" vs "Consolidate UTXOs"). Defaults to [HardwareConfirmationContent.SignTransaction].
 * @property pendingBodyModel: Optional lambda to provide operation-specific content for the
 * "pending" screen shown when user taps before approving/denying on the device. If null, the
 * default pending screen is shown.
 * @property deniedBodyModel: Optional lambda to provide operation-specific content for the
 * "denied" screen shown when user explicitly denies on the device. If null, the flow is canceled.
 */
data class SignTransactionNfcSessionUiProps(
  val account: FullAccount,
  val psbt: Psbt,
  val spendingKeyset: SpendingKeyset? = null,
  val useRecoveryHwAuthKey: Boolean = false,
  val hardwareTypeOverride: HardwareType? = null,
  val skipPairingCheck: Boolean = false,
  val skipFirmwareTelemetry: Boolean = false,
  val onBack: () -> Unit,
  val onSuccess: (Psbt) -> Unit,
  val onError: (NfcException) -> Boolean = { false },
  val eventTrackerContext: NfcEventTrackerScreenIdContext = SIGN_TRANSACTION,
  val showNativeSheetOnIos: Boolean = true,
  val confirmationContent: HardwareConfirmationContent = HardwareConfirmationContent.SignTransaction,
  val pendingBodyModel: (
    (
      onAcknowledge: () -> Unit,
    ) -> HardwareConfirmationResultBodyModel
  )? = null,
  val deniedBodyModel: ((onAcknowledge: () -> Unit) -> HardwareConfirmationResultBodyModel)? = null,
)
