package build.wallet.bitcoin.transactions

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map

@BitkeyInject(AppScope::class)
class TransactionPriorityPreferenceImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : TransactionPriorityPreference {
  override suspend fun get(): EstimatedTransactionPriority? {
    return databaseProvider.database()
      .priorityPreferenceQueries
      .getPriorityPreference()
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
    databaseProvider.database()
      .priorityPreferenceQueries
      .setPriorityPreference(priority)
  }

  override suspend fun clear(): Result<Unit, Error> {
    return databaseProvider.database()
      .awaitTransaction {
        priorityPreferenceQueries.clear()
      }
  }
}
