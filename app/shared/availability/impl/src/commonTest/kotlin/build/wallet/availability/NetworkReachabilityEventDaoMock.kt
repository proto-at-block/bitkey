package build.wallet.availability

import app.cash.turbine.Turbine
import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.datetime.Instant

class NetworkReachabilityEventDaoMock(
  turbine: (String) -> Turbine<Any?>,
) : NetworkReachabilityEventDao {
  val insertReachabilityEventCalls = turbine("insertReachabilityEvent calls")
  var insertReachabilityEventResult = Ok(Unit)

  override suspend fun insertReachabilityEvent(
    connection: NetworkConnection,
    reachability: NetworkReachability,
  ): Result<Unit, DbError> {
    insertReachabilityEventCalls.add(Pair(connection, reachability))
    return insertReachabilityEventResult
  }

  val getMostRecentReachableEventCalls = turbine("getMostRecentReachableEvent calls")
  var getMostRecentReachableEventResult: Result<Instant?, DbError> = Ok(null)

  override suspend fun getMostRecentReachableEvent(
    connection: NetworkConnection?,
  ): Result<Instant?, DbError> {
    getMostRecentReachableEventCalls.add(connection)
    return getMostRecentReachableEventResult
  }
}
