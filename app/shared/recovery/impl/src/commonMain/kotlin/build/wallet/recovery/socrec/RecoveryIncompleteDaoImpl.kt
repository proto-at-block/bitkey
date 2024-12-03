package build.wallet.recovery.socrec

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.*

class RecoveryIncompleteDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : RecoveryIncompleteDao {
  override fun recoveryIncomplete(): Flow<Boolean> {
    return flow {
      databaseProvider.database()
        .recoveryIncompleteQueries
        .getRecoveryIncomplete()
        .asFlowOfOneOrNull()
        .map { (result) -> result?.incomplete ?: false }
        .distinctUntilChanged()
        .collect(::emit)
    }
  }

  override suspend fun setRecoveryIncomplete(incomplete: Boolean): Result<Unit, DbError> {
    return databaseProvider.database()
      .awaitTransaction {
        recoveryIncompleteQueries.setRecoveryIncomplete(incomplete)
      }
  }
}
