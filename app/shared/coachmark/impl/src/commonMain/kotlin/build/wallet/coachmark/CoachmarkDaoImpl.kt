package build.wallet.coachmark

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.sqldelight.awaitAsListResult
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.datetime.Instant

class CoachmarkDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : CoachmarkDao {
  private val database by lazy {
    databaseProvider.database()
  }

  override suspend fun insertCoachmark(
    id: CoachmarkIdentifier,
    expiration: Instant,
  ): Result<Unit, DbError> =
    database.coachmarksQueries
      .awaitTransactionWithResult {
        createCoachmark(
          id = id,
          viewed = false,
          expiration = expiration
        )
      }

  override suspend fun setViewed(id: CoachmarkIdentifier): Result<Unit, DbError> =
    database.coachmarksQueries
      .awaitTransactionWithResult {
        setViewed(true, id)
      }

  override suspend fun getCoachmark(id: CoachmarkIdentifier): Result<Coachmark?, DbError> =
    database
      .coachmarksQueries
      .getCoachmark(id)
      .awaitAsOneOrNullResult()
      .map { entity ->
        entity?.let {
          Coachmark(
            id = id,
            viewed = it.viewed,
            expiration = it.expiration
          )
        }
      }

  override suspend fun getAllCoachmarks(): Result<List<Coachmark>, DbError> =
    database
      .coachmarksQueries
      .getAllCoachmarks()
      .awaitAsListResult()
      .map { entities ->
        entities.map {
          Coachmark(
            id = it.id,
            viewed = it.viewed,
            expiration = it.expiration
          )
        }
      }

  override suspend fun resetCoachmarks(): Result<Unit, DbError> =
    database
      .coachmarksQueries
      .awaitTransactionWithResult {
        reset()
      }.logFailure {
        "Failed to reset coachmarks"
      }
}
