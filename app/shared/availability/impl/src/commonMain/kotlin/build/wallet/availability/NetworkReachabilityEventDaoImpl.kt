package build.wallet.availability

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.BitkeyDatabase
import build.wallet.db.DbError
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class NetworkReachabilityEventDaoImpl(
  private val clock: Clock,
  private val databaseProvider: BitkeyDatabaseProvider,
) : NetworkReachabilityEventDao {
  private suspend fun database(): BitkeyDatabase {
    return databaseProvider.database()
  }

  override suspend fun insertReachabilityEvent(
    connection: NetworkConnection,
    reachability: NetworkReachability,
  ): Result<Unit, DbError> {
    return database().awaitTransaction {
      networkReachabilityEventQueries.insertEvent(
        connection = connection,
        reachability = reachability,
        timestamp = clock.now()
      )
    }
  }

  override suspend fun getMostRecentReachableEvent(
    connection: NetworkConnection?,
  ): Result<Instant?, DbError> {
    return when (connection) {
      null ->
        database().networkReachabilityEventQueries.getMostRecentEvent(
          NetworkReachability.REACHABLE
        )
      else ->
        database().networkReachabilityEventQueries.getMostRecentEventForConnection(
          connection,
          NetworkReachability.REACHABLE
        )
    }
      .awaitAsOneOrNullResult()
      .map { it?.timestamp }
  }
}
