package build.wallet.statemachine.send

import build.wallet.bitcoin.invoice.ParsedPaymentData
import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface SendUiStateMachine : StateMachine<SendUiProps, ScreenModel>

/**
 * @property onExit - handler for exiting this state machine.
 * @property validInvoiceInClipboard payment information in clipboard (if any)
 * @property onDone handler for what the "Done" button does in the transfer confirmation screen.
 */
data class SendUiProps(
  val account: FullAccount,
  val validInvoiceInClipboard: ParsedPaymentData?,
  val onExit: () -> Unit,
  val onDone: () -> Unit,
  val onGoToUtxoConsolidation: () -> Unit,
)
