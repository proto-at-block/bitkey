package build.wallet.bitcoin.metadata

import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.db.DbError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface WalletMetadataDao {
  suspend fun upsertTransactionNote(
    accountId: WalletMetadataAccountId,
    note: TransactionNote,
  ): Result<Unit, DbError>

  /**
   * Deletes the note for the given transaction and records a
   * [WalletMetadataTombstone.DeletedTransactionNote] so the deletion can be propagated
   * by future metadata sync/backup work.
   */
  suspend fun deleteTransactionNote(
    accountId: WalletMetadataAccountId,
    transactionId: BitcoinTransactionId,
    deletedAt: Instant,
  ): Result<Unit, DbError>

  /**
   * Emits all transaction notes for the given account, updating whenever notes
   * change in the database.
   */
  fun transactionNotes(
    accountId: WalletMetadataAccountId,
  ): Flow<Result<List<TransactionNote>, DbError>>

  suspend fun transactionNote(
    accountId: WalletMetadataAccountId,
    transactionId: BitcoinTransactionId,
  ): Result<TransactionNote?, DbError>

  /**
   * Emits the note for the given transaction (or `null` when none exists), updating
   * whenever the row changes in the database. Observes only the single row rather than
   * all notes for the account.
   */
  fun observeTransactionNote(
    accountId: WalletMetadataAccountId,
    transactionId: BitcoinTransactionId,
  ): Flow<Result<TransactionNote?, DbError>>

  suspend fun tombstones(
    accountId: WalletMetadataAccountId,
  ): Result<Set<WalletMetadataTombstone>, DbError>

  suspend fun snapshot(
    accountId: WalletMetadataAccountId,
  ): Result<WalletMetadataSnapshot?, DbError>

  suspend fun mergeSnapshot(
    snapshot: WalletMetadataSnapshot,
  ): Result<WalletMetadataSnapshot, DbError>
}
