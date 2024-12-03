package build.wallet.inheritance

import build.wallet.bitkey.inheritance.InheritanceMaterialHash
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbTransactionError
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import kotlinx.datetime.Clock

class InheritanceSyncDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
  private val clock: Clock,
) : InheritanceSyncDao {
  override suspend fun getSyncedInheritanceMaterialHash(): Result<InheritanceMaterialHash?, DbTransactionError> {
    return databaseProvider.database().awaitTransactionWithResult {
      inheritanceDataQueries
        .getSyncHash()
        .executeAsOneOrNull()
    }
  }

  override suspend fun updateSyncedInheritanceMaterialHash(
    hash: InheritanceMaterialHash,
  ): Result<Unit, DbTransactionError> {
    return databaseProvider.database().awaitTransactionWithResult {
      inheritanceDataQueries.updateHash(
        hash,
        clock.now()
      )
    }
  }
}
