package build.wallet.availability

import app.cash.turbine.Turbine
import build.wallet.ktor.result.HttpError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class InternetNetworkReachabilityServiceMock(
  turbine: (String) -> Turbine<Any>,
) : InternetNetworkReachabilityService {
  val checkConnectionCalls = turbine("checkConnection for internet calls")
  var checkConnectionResult: Result<Unit, HttpError> = Ok(Unit)

  override suspend fun checkConnection(): Result<Unit, HttpError> {
    checkConnectionCalls.add(Unit)
    return checkConnectionResult
  }

  fun reset() {
    checkConnectionResult = Ok(Unit)
  }
}
