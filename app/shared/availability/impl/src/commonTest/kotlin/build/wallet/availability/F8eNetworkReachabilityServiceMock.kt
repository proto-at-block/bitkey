package build.wallet.availability

import app.cash.turbine.Turbine
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.HttpError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class F8eNetworkReachabilityServiceMock(
  turbine: (String) -> Turbine<Any>,
) : F8eNetworkReachabilityService {
  val checkConnectionCalls = turbine("checkConnection for f8e calls")
  var checkConnectionResult: Result<Unit, HttpError> = Ok(Unit)

  override suspend fun checkConnection(f8eEnvironment: F8eEnvironment): Result<Unit, HttpError> {
    checkConnectionCalls.add(Unit)
    return checkConnectionResult
  }

  fun reset() {
    checkConnectionResult = Ok(Unit)
  }
}
