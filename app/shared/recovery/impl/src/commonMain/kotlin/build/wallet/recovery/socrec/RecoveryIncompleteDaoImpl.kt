package build.wallet.recovery.socrec

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RecoveryIncompleteDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : RecoveryIncompleteDao {
  private val database by lazy { databaseProvider.database() }

  override fun recoveryIncomplete(): Flow<Boolean> {
    return database
      .recoveryIncompleteQueries
      .getRecoveryIncomplete()
      .asFlowOfOneOrNull()
      .map {
        it.component1()?.incomplete ?: false
      }
      .distinctUntilChanged()
  }

  override suspend fun setRecoveryIncomplete(incomplete: Boolean): Result<Unit, DbError> {
    return database
      .awaitTransaction {
        database.recoveryIncompleteQueries.setRecoveryIncomplete(incomplete)
      }
  }
}
