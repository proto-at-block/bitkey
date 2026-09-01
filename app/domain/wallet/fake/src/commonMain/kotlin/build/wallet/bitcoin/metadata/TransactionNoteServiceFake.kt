package build.wallet.bitcoin.metadata

import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

class TransactionNoteServiceFake : TransactionNoteService {
  private val notesById =
    MutableStateFlow<Map<BitcoinTransactionId, TransactionNote>>(emptyMap())
  private val clock = ClockFake()

  var createOrUpdateNoteError: TransactionNoteServiceError? = null
  var deleteNoteError: TransactionNoteServiceError? = null
  var notesError: TransactionNoteServiceError? = null

  override fun notes(): Flow<Result<Map<BitcoinTransactionId, TransactionNote>, TransactionNoteServiceError>> =
    notesById.map { notes ->
      when (val error = notesError) {
        null -> Ok(notes)
        else -> Err(error)
      }
    }

  override fun note(
    transactionId: BitcoinTransactionId,
  ): Flow<Result<TransactionNote?, TransactionNoteServiceError>> =
    notesById.map { notes ->
      when (val error = notesError) {
        null -> Ok(notes[transactionId])
        else -> Err(error)
      }
    }

  override suspend fun createOrUpdateNote(
    transactionId: BitcoinTransactionId,
    note: String,
  ): Result<TransactionNote, TransactionNoteServiceError> {
    createOrUpdateNoteError?.let { return Err(it) }
    val normalizedNoteResult = TransactionNote.normalize(note)
    if (normalizedNoteResult.isErr) {
      return Err(normalizedNoteResult.error)
    }
    val existingNote = notesById.value[transactionId]
    val now = clock.now()
    val transactionNote = TransactionNote(
      transactionId = transactionId,
      note = normalizedNoteResult.value,
      createdAt = existingNote?.createdAt ?: now,
      updatedAt = now
    )

    notesById.value += (transactionId to transactionNote)
    return Ok(transactionNote)
  }

  override suspend fun deleteNote(
    transactionId: BitcoinTransactionId,
  ): Result<Unit, TransactionNoteServiceError> {
    deleteNoteError?.let { return Err(it) }
    notesById.value -= transactionId
    return Ok(Unit)
  }

  fun setNote(
    transactionId: BitcoinTransactionId,
    note: String,
    createdAt: Instant = clock.now(),
    updatedAt: Instant = createdAt,
  ) {
    notesById.value += (
      transactionId to TransactionNote(
        transactionId = transactionId,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
      )
    )
  }

  fun reset() {
    notesById.value = emptyMap()
    clock.reset()
    createOrUpdateNoteError = null
    deleteNoteError = null
    notesError = null
  }
}
