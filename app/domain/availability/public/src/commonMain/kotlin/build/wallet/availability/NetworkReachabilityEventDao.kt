package build.wallet.availability

import com.github.michaelbull.result.Result
import kotlinx.datetime.Instant

/**
 * DAO (Data Access Object) for [NetworkReachabilityEventEntity] database table.
 */
interface NetworkReachabilityEventDao {
  /**
   * Inserts or updates an event (using [Clock.now] as the time) for the
   * given connection and reachability.
   */
  suspend fun insertReachabilityEvent(
    connection: NetworkConnection,
    reachability: NetworkReachability,
  ): Result<Unit, Error>

  /**
   * Returns the most recent event for the given connection that is REACHABLE,
   * or null if there is no corresponding event stored.
   *
   * @param connection - The connection to look up an event for, or null to look
   * up the last REACHABLE event, regardless of connection.
   */
  suspend fun getMostRecentReachableEvent(connection: NetworkConnection?): Result<Instant?, Error>
}
