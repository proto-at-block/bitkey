package build.wallet.coachmark

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result

@BitkeyInject(AppScope::class)
class Bip177CoachmarkEligibilityDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : Bip177CoachmarkEligibilityDao {
  override suspend fun getEligibility(): Result<Boolean?, DbError> =
    databaseProvider.database()
      .bip177CoachmarkEligibilityQueries
      .getBip177CoachmarkEligibility()
      .awaitAsOneOrNullResult()

  override suspend fun setEligibility(eligible: Boolean): Result<Unit, DbError> =
    databaseProvider.database()
      .bip177CoachmarkEligibilityQueries
      .awaitTransactionWithResult {
        setBip177CoachmarkEligibility(eligible)
      }
}
