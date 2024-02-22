package build.wallet.statemachine.send

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.invoice.BitcoinInvoice
import build.wallet.bitcoin.invoice.ParsedPaymentData
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State Machine for showing Qr Code scan overlay
 */
interface BitcoinQrCodeUiScanStateMachine : StateMachine<BitcoinQrCodeScanUiProps, ScreenModel>

/**
 * @property networkType Bitcoin network that a customer's currently active keyset belongs to.
 * @property spendingWallet The user's spending wallet. Used to determine if an address belongs to self.
 * @property validInvoiceInClipboard Valid payment information in the user's clipboard (if any).
 * @property onEnterAddressClick - Method to be invoked when the user wants to manually enter an
 * address
 * @property onClose - Method invoked when the user wants to close the qr scan
 * @property onRecipientScanned - Method invoked when a valid bitcoin address has been scanned
 */
data class BitcoinQrCodeScanUiProps(
  val networkType: BitcoinNetworkType,
  val spendingWallet: SpendingWallet,
  val validInvoiceInClipboard: ParsedPaymentData?,
  val onEnterAddressClick: () -> Unit,
  val onClose: () -> Unit,
  val onRecipientScanned: (address: BitcoinAddress) -> Unit,
  val onInvoiceScanned: (address: BitcoinInvoice) -> Unit,
)
