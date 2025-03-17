package build.wallet.inheritance

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map

@BitkeyInject(AppScope::class)
class InheritanceUpsellViewDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : InheritanceUpsellViewDao {
  override suspend fun insert(id: String): Result<Unit, DbError> =
    databaseProvider.database()
      .inheritanceUpsellViewQueries
      .awaitTransactionWithResult {
        insert(id)
      }.logFailure {
        "Failed to insert inheritance upsell view"
      }

  override suspend fun setViewed(id: String): Result<Unit, DbError> =
    databaseProvider.database()
      .inheritanceUpsellViewQueries
      .awaitTransactionWithResult {
        setViewed(id)
      }.logFailure {
        "Failed to set inheritance upsell as viewed"
      }

  override suspend fun get(id: String): Result<Boolean, DbError> =
    databaseProvider.database()
      .inheritanceUpsellViewQueries
      .get(id)
      .awaitAsOneOrNullResult()
      .map { it?.viewed == 1L }
      .logFailure {
        "Failed to get inheritance upsell view status"
      }
}
