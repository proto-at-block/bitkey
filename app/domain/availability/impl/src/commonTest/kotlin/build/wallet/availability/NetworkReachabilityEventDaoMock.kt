package build.wallet.availability

import app.cash.turbine.Turbine
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
  ): Result<Unit, Error> {
    insertReachabilityEventCalls.add(Pair(connection, reachability))
    return insertReachabilityEventResult
  }

  val getMostRecentReachableEventCalls = turbine("getMostRecentReachableEvent calls")
  var getMostRecentReachableEventResult: Result<Instant?, Error> = Ok(null)

  override suspend fun getMostRecentReachableEvent(
    connection: NetworkConnection?,
  ): Result<Instant?, Error> {
    getMostRecentReachableEventCalls.add(connection)
    return getMostRecentReachableEventResult
  }
}
