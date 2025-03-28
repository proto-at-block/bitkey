package build.wallet.statemachine.send

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.invoice.ParsedPaymentData
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for entering a Bitcoin address as a recipient.
 */
interface BitcoinAddressRecipientUiStateMachine : StateMachine<BitcoinAddressRecipientUiProps, BodyModel>

/**
 * @property address - default valid Bitcoin address to be pre-selected.
 * @property networkType network type of the customer's active keybox
 * @property spendingKeyset active spending keyset of the customer
 * @property validInvoiceInClipboard a valid invoice inside the user's clipboard (if any)
 * @property onBack - handler for back navigation.
 * @property onRecipientEntered - handler for when a valid address is entered and selected as a
 * recipient address.
 * @property onScanQrCodeClick - handler for actions on qr code scan button
 */
data class BitcoinAddressRecipientUiProps(
  val address: BitcoinAddress?,
  val validInvoiceInClipboard: ParsedPaymentData?,
  val onBack: () -> Unit,
  val onRecipientEntered: (address: BitcoinAddress) -> Unit,
  val onScanQrCodeClick: () -> Unit,
  val onGoToUtxoConsolidation: () -> Unit,
)
