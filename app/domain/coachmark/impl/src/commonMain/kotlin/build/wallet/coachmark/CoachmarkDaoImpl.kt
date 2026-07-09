package build.wallet.coachmark

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
class CoachmarkDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : CoachmarkDao {
  override suspend fun insertCoachmark(
    id: CoachmarkIdentifier,
    expiration: Instant?,
  ): Result<Unit, DbError> =
    databaseProvider.database()
      .coachmarksQueries
      .awaitTransactionWithResult {
        createCoachmark(
          id = id,
          viewed = false,
          expiration = expiration
        )
      }

  override suspend fun setViewed(id: CoachmarkIdentifier): Result<Unit, DbError> =
    databaseProvider.database()
      .coachmarksQueries
      .awaitTransactionWithResult {
        setViewed(true, id)
      }

  override suspend fun getCoachmark(id: CoachmarkIdentifier): Result<Coachmark?, DbError> =
    databaseProvider.database()
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

  override suspend fun getAllCoachmarks(): Result<List<Coachmark>, DbError> {
    val database = databaseProvider.database()
    // Prune rows whose id no longer corresponds to a known CoachmarkIdentifier
    // (e.g. coachmarks that existed in previous app versions and have since been
    // removed from the enum). Done here so cleanup happens self-healingly on read.
    return database.coachmarksQueries
      .awaitTransactionWithResult {
        deleteUnknownCoachmarks(KNOWN_RAW_COACHMARK_IDS)
        getAllCoachmarks().executeAsList().map {
          Coachmark(
            id = it.id,
            viewed = it.viewed,
            expiration = it.expiration
          )
        }
      }
      .logFailure { "Failed to prune + read coachmarks" }
  }

  override suspend fun resetCoachmarks(): Result<Unit, DbError> =
    databaseProvider.database()
      .coachmarksQueries
      .awaitTransactionWithResult {
        reset()
      }.logFailure {
        "Failed to reset coachmarks"
      }

  private companion object {
    // The raw strings considered valid in the `id` column.
    val KNOWN_RAW_COACHMARK_IDS: List<String> =
      CoachmarkIdentifier.entries
        .filter { it != CoachmarkIdentifier.Unknown }
        .map { it.id }
  }
}
