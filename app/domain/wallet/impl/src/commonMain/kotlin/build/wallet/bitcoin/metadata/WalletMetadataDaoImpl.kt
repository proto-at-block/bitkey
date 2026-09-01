package build.wallet.bitcoin.metadata

import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.catchingResult
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.WalletMetadataTransactionNoteEntity
import build.wallet.database.sqldelight.WalletMetadataTransactionNoteTombstoneEntity
import build.wallet.db.DbError
import build.wallet.db.DbQueryError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.sqldelight.asFlowOfList
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsListResult
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

@BitkeyInject(AppScope::class)
class WalletMetadataDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : WalletMetadataDao {
  override suspend fun upsertTransactionNote(
    accountId: WalletMetadataAccountId,
    note: TransactionNote,
  ): Result<Unit, DbError> =
    databaseProvider.database()
      .walletMetadataQueries
      .awaitTransactionWithResult {
        deleteTransactionNoteTombstone(accountId, note.transactionId)
        upsertTransactionNote(
          accountId = accountId,
          transactionId = note.transactionId,
          note = note.note,
          createdAt = note.createdAt,
          updatedAt = note.updatedAt
        )
      }

  override suspend fun deleteTransactionNote(
    accountId: WalletMetadataAccountId,
    transactionId: BitcoinTransactionId,
    deletedAt: Instant,
  ): Result<Unit, DbError> =
    databaseProvider.database()
      .walletMetadataQueries
      .awaitTransactionWithResult {
        deleteTransactionNote(accountId, transactionId)
        upsertDeletedTransactionNote(accountId, transactionId, deletedAt)
      }

  override fun transactionNotes(
    accountId: WalletMetadataAccountId,
  ): Flow<Result<List<TransactionNote>, DbError>> =
    flow {
      emitAll(
        databaseProvider.database()
          .walletMetadataQueries
          .selectTransactionNotesForAccount(accountId)
          .asFlowOfList()
          .map { result ->
            result.andThen { entities ->
              // Validate persisted rows inside the Result boundary: a corrupt row
              // surfaces as a DbError instead of throwing.
              catchingResult { entities.map { it.toTransactionNote() } }
                .mapError { DbQueryError(cause = it) }
            }
          }
      )
    }

  override fun observeTransactionNote(
    accountId: WalletMetadataAccountId,
    transactionId: BitcoinTransactionId,
  ): Flow<Result<TransactionNote?, DbError>> =
    flow {
      emitAll(
        databaseProvider.database()
          .walletMetadataQueries
          .selectTransactionNote(accountId, transactionId)
          .asFlowOfOneOrNull()
          .map { result ->
            result.andThen { entity ->
              // Validate the persisted row inside the Result boundary: a corrupt row
              // surfaces as a DbError instead of throwing.
              catchingResult { entity?.toTransactionNote() }
                .mapError { DbQueryError(cause = it) }
            }
          }
      )
    }

  override suspend fun transactionNote(
    accountId: WalletMetadataAccountId,
    transactionId: BitcoinTransactionId,
  ): Result<TransactionNote?, DbError> =
    databaseProvider.database()
      .walletMetadataQueries
      .selectTransactionNote(accountId, transactionId)
      .awaitAsOneOrNullResult()
      .andThen { entity ->
        catchingResult { entity?.toTransactionNote() }
          .mapError { DbQueryError(cause = it) }
      }

  override suspend fun tombstones(
    accountId: WalletMetadataAccountId,
  ): Result<Set<WalletMetadataTombstone>, DbError> =
    databaseProvider.database()
      .walletMetadataQueries
      .selectDeletedTransactionNotesForAccount(accountId)
      .awaitAsListResult()
      .andThen { deletedNotes ->
        catchingResult { deletedNotes.map { it.toTombstone() }.toSet() }
          .mapError { DbQueryError(cause = it) }
      }

  override suspend fun snapshot(
    accountId: WalletMetadataAccountId,
  ): Result<WalletMetadataSnapshot?, DbError> = coroutineBinding {
    val notes = databaseProvider.database()
      .walletMetadataQueries
      .selectTransactionNotesForAccount(accountId)
      .awaitAsListResult()
      .andThen { entities ->
        catchingResult { entities.map { it.toTransactionNote() }.toSet() }
          .mapError { DbQueryError(cause = it) }
      }
      .bind()
    val tombstones = tombstones(accountId).bind()
    if (notes.isEmpty() && tombstones.isEmpty()) {
      null
    } else {
      WalletMetadataSnapshot(
        accountId = accountId,
        transactionNotes = notes,
        tombstones = tombstones
      )
    }
  }

  override suspend fun mergeSnapshot(
    snapshot: WalletMetadataSnapshot,
  ): Result<WalletMetadataSnapshot, DbError> =
    databaseProvider.database()
      .walletMetadataQueries
      .awaitTransactionWithResult {
        // Read, merge, clear, and rewrite inside a single transaction so a concurrent local note
        // edit cannot land between the read and the rewrite and be silently deleted.
        val existingNotes = selectTransactionNotesForAccount(snapshot.accountId)
          .executeAsList()
          .map { it.toTransactionNote() }
          .toSet()
        val existingTombstones = selectDeletedTransactionNotesForAccount(snapshot.accountId)
          .executeAsList()
          .map { it.toTombstone() }
          .toSet()
        val existing = if (existingNotes.isEmpty() && existingTombstones.isEmpty()) {
          null
        } else {
          WalletMetadataSnapshot(
            accountId = snapshot.accountId,
            transactionNotes = existingNotes,
            tombstones = existingTombstones
          )
        }
        val merged = mergeWalletMetadataSnapshots(existing, snapshot) ?: snapshot
        clearTransactionNotesForAccount(snapshot.accountId)
        clearTransactionNoteTombstonesForAccount(snapshot.accountId)
        merged.transactionNotes.forEach { note ->
          upsertTransactionNote(
            accountId = snapshot.accountId,
            transactionId = note.transactionId,
            note = note.note,
            createdAt = note.createdAt,
            updatedAt = note.updatedAt
          )
        }
        merged.tombstones
          .filterIsInstance<WalletMetadataTombstone.DeletedTransactionNote>()
          .forEach { tombstone ->
            upsertDeletedTransactionNote(
              accountId = snapshot.accountId,
              transactionId = tombstone.transactionId,
              deletedAt = tombstone.deletedAt
            )
          }
        merged
      }
}

private fun WalletMetadataTransactionNoteEntity.toTransactionNote(): TransactionNote =
  TransactionNote(
    transactionId = transactionId,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt
  )

private fun WalletMetadataTransactionNoteTombstoneEntity.toTombstone():
  WalletMetadataTombstone.DeletedTransactionNote =
  WalletMetadataTombstone.DeletedTransactionNote(
    transactionId = transactionId,
    deletedAt = deletedAt
  )
