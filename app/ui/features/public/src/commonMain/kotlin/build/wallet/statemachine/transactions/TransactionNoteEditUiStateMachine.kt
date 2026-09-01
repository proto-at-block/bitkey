package build.wallet.statemachine.transactions

import build.wallet.bitcoin.metadata.TransactionNote
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for the half-sheet used to add, edit, or delete a customer note on a
 * transaction. Presenting this as a bottom sheet (instead of a full modal screen) keeps
 * the note editing flow in-system with the transaction detail screen underneath.
 *
 * Only compose this state machine while the sheet is showing so that draft state is
 * discarded on dismiss and each open starts from the persisted note.
 */
interface TransactionNoteEditUiStateMachine :
  StateMachine<TransactionNoteEditUiProps, SheetModel>

data class TransactionNoteEditUiProps(
  val transactionId: BitcoinTransactionId,
  val existingNote: TransactionNote?,
  val onClose: () -> Unit,
)
