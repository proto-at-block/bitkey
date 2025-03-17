package build.wallet.onboarding

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.datetime.Instant

@BitkeyInject(AppScope::class)
class OnboardingCompletionDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : OnboardingCompletionDao {
  override suspend fun recordCompletion(
    id: String,
    timestamp: Instant,
  ): Result<Unit, DbError> =
    databaseProvider.database()
      .onboardingCompletionQueries
      .awaitTransactionWithResult {
        insert(id, timestamp.toEpochMilliseconds())
      }.logFailure {
        "Failed to record onboarding completion"
      }

  override suspend fun recordCompletionIfNotExists(
    id: String,
    timestamp: Instant,
  ): Result<Unit, DbError> =
    databaseProvider.database()
      .onboardingCompletionQueries
      .awaitTransactionWithResult {
        val existingRecord = get(id).executeAsOneOrNull()
        if (existingRecord == null) {
          // Insert the new record if it doesn't exist
          insert(id, timestamp.toEpochMilliseconds())
        }
      }.logFailure {
        "Failed to record onboarding completion"
      }

  override suspend fun getCompletionTimestamp(id: String): Result<Instant?, DbError> =
    databaseProvider.database()
      .onboardingCompletionQueries
      .get(id)
      .awaitAsOneOrNullResult()
      .map { row ->
        row?.completion_timestamp?.let { Instant.fromEpochMilliseconds(it) }
      }
      .logFailure {
        "Failed to get onboarding completion timestamp"
      }

  override suspend fun clearCompletionTimestamp(id: String): Result<Unit, DbError> =
    databaseProvider.database()
      .onboardingCompletionQueries
      .awaitTransactionWithResult {
        reset()
      }.logFailure {
        "Failed to clear onboarding completion timestamp"
      }
}
