package build.wallet.bitcoin.metadata

import build.wallet.bitcoin.transactions.BitcoinTransactionId
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Domain service for managing customer-authored notes attached to the active account's
 * on-chain transactions.
 *
 * Notes are local metadata only — they never affect wallet behavior. The active account is
 * resolved internally, following the same pattern as sibling wallet services.
 */
interface TransactionNoteService {
  /**
   * Emits transaction notes for the currently active account, keyed by transaction id.
   * Emits updates whenever notes change in the database. Emits an empty map when there
   * is no active account.
   */
  fun notes(): Flow<Result<Map<BitcoinTransactionId, TransactionNote>, TransactionNoteServiceError>>

  /**
   * Emits the note for the given transaction on the currently active account, or `null`
   * when there is no note (or no active account). Observes only the single note rather
   * than all notes for the account — prefer this for single-transaction surfaces like
   * the transaction details screen.
   */
  fun note(
    transactionId: BitcoinTransactionId,
  ): Flow<Result<TransactionNote?, TransactionNoteServiceError>>

  /**
   * Creates a new note, or updates the existing note, for the given transaction on the
   * active account. The note is normalized via [TransactionNote.normalize] before persisting.
   */
  suspend fun createOrUpdateNote(
    transactionId: BitcoinTransactionId,
    note: String,
  ): Result<TransactionNote, TransactionNoteServiceError>

  /**
   * Deletes the note for the given transaction on the active account, if one exists.
   */
  suspend fun deleteNote(
    transactionId: BitcoinTransactionId,
  ): Result<Unit, TransactionNoteServiceError>
}

sealed class TransactionNoteServiceError : Error() {
  /**
   * The submitted note content is not valid. Deliberately does not carry the raw note
   * content to keep customer data out of logs — callers already have the note they submitted.
   */
  data class InvalidNote(
    override val message: String,
  ) : TransactionNoteServiceError()

  data class PersistenceFailed(
    override val cause: Error,
  ) : TransactionNoteServiceError() {
    override val message: String = "Failed to persist transaction notes."
  }

  data object NoActiveAccount : TransactionNoteServiceError() {
    override val message: String = "No active account to manage transaction notes for."
  }
}
