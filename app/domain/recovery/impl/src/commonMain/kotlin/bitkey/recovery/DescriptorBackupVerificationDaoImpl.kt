package bitkey.recovery

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map

@BitkeyInject(AppScope::class)
class DescriptorBackupVerificationDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : DescriptorBackupVerificationDao {
  override suspend fun getVerifiedBackup(keysetId: String): Result<VerifiedBackup?, DbError> =
    databaseProvider.database()
      .descriptorBackupVerificationQueries
      .getVerifiedBackup(keysetId)
      .awaitAsOneOrNullResult()
      .map { entity ->
        entity?.let {
          VerifiedBackup(
            keysetId = it
          )
        }
      }

  override suspend fun replaceAllVerifiedBackups(
    backups: List<VerifiedBackup>,
  ): Result<Unit, DbError> =
    databaseProvider.database()
      .descriptorBackupVerificationQueries
      .awaitTransactionWithResult {
        // Clear all existing records
        clearAllVerifiedBackups()

        // Insert new records
        backups.forEach { backup ->
          setVerifiedBackup(keysetId = backup.keysetId)
        }
      }

  override suspend fun clear(): Result<Unit, DbError> =
    databaseProvider.database()
      .descriptorBackupVerificationQueries
      .awaitTransactionWithResult {
        clearAllVerifiedBackups()
      }
}
