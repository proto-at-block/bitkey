package build.wallet.bitcoin.transactions

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.logging.logFailure
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map

class TransactionPriorityPreferenceImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : TransactionPriorityPreference {
  private val db by lazy {
    databaseProvider.database()
  }

  override suspend fun get(): EstimatedTransactionPriority? {
    return db.priorityPreferenceQueries.getPriorityPreference()
      .awaitAsOneOrNullResult()
      .logFailure { "Unable to get priority entity" }
      .map {
        it?.priority
      }
      .getOrElse {
        // when there is an error retrieving just return null
        null
      }
  }

  override suspend fun set(priority: EstimatedTransactionPriority) {
    db.priorityPreferenceQueries.setPriorityPreference(priority)
  }

  override suspend fun clear(): Result<Unit, Error> {
    return db.awaitTransaction {
      priorityPreferenceQueries.clear()
    }
  }
}
