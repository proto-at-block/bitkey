@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.bitcoin.metadata

import bitkey.recovery.WalletMetadataServerBackupService
import build.wallet.account.AccountService
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

@BitkeyInject(AppScope::class)
class TransactionNoteServiceImpl(
  private val accountService: AccountService,
  private val walletMetadataDao: WalletMetadataDao,
  private val walletMetadataServerBackupService: WalletMetadataServerBackupService,
  private val clock: Clock,
) : TransactionNoteService {
  override fun notes(): Flow<Result<Map<BitcoinTransactionId, TransactionNote>, TransactionNoteServiceError>> =
    accountService.activeAccount()
      .flatMapLatest { account ->
        when (account) {
          null -> flowOf(Ok(emptyMap<BitcoinTransactionId, TransactionNote>()))
          else ->
            walletMetadataDao
              .transactionNotes(WalletMetadataAccountId.fromAccountId(account.accountId))
              .map { result ->
                result
                  .map { notes -> notes.associateBy { it.transactionId } }
                  .mapError { TransactionNoteServiceError.PersistenceFailed(it) }
              }
        }
      }

  override fun note(
    transactionId: BitcoinTransactionId,
  ): Flow<Result<TransactionNote?, TransactionNoteServiceError>> =
    accountService.activeAccount()
      .flatMapLatest { account ->
        when (account) {
          null -> flowOf(Ok(null))
          else ->
            walletMetadataDao
              .observeTransactionNote(
                accountId = WalletMetadataAccountId.fromAccountId(account.accountId),
                transactionId = transactionId
              )
              .map { result ->
                result.mapError { TransactionNoteServiceError.PersistenceFailed(it) }
              }
        }
      }

  override suspend fun createOrUpdateNote(
    transactionId: BitcoinTransactionId,
    note: String,
  ): Result<TransactionNote, TransactionNoteServiceError> =
    coroutineBinding {
      val accountId = activeMetadataAccountId().bind()
      val normalizedNote = TransactionNote.normalize(note).bind()
      val existingNote = walletMetadataDao
        .transactionNote(accountId, transactionId)
        .mapError { TransactionNoteServiceError.PersistenceFailed(it) }
        .bind()
      val now = clock.now()
      val transactionNote = TransactionNote(
        transactionId = transactionId,
        note = normalizedNote,
        // Guard against device clock rollback: a persisted createdAt later than "now"
        // would otherwise violate the updatedAt >= createdAt invariant.
        createdAt = existingNote?.createdAt?.takeIf { it <= now } ?: now,
        updatedAt = now
      )

      walletMetadataDao
        .upsertTransactionNote(accountId, transactionNote)
        .mapError { TransactionNoteServiceError.PersistenceFailed(it) }
        .bind()

      // Request server backup of the edit. Best-effort: the local edit must never fail or
      // roll back because backup scheduling failed.
      walletMetadataServerBackupService.requestBackup(accountId)
        .logFailure { "Failed to request wallet metadata backup after note edit" }

      transactionNote
    }

  override suspend fun deleteNote(
    transactionId: BitcoinTransactionId,
  ): Result<Unit, TransactionNoteServiceError> =
    coroutineBinding {
      val accountId = activeMetadataAccountId().bind()
      walletMetadataDao
        .deleteTransactionNote(
          accountId = accountId,
          transactionId = transactionId,
          deletedAt = clock.now()
        )
        .mapError { TransactionNoteServiceError.PersistenceFailed(it) }
        .bind()

      // Request server backup of the deletion. Best-effort: the local edit must never fail or
      // roll back because backup scheduling failed.
      walletMetadataServerBackupService.requestBackup(accountId)
        .logFailure { "Failed to request wallet metadata backup after note deletion" }
    }

  private suspend fun activeMetadataAccountId(): Result<WalletMetadataAccountId, TransactionNoteServiceError> =
    when (val account = accountService.activeAccount().first()) {
      null -> Err(TransactionNoteServiceError.NoActiveAccount)
      else -> Ok(WalletMetadataAccountId.fromAccountId(account.accountId))
    }
}
