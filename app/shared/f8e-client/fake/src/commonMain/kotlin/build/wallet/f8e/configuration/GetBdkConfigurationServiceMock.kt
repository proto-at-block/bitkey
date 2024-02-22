package build.wallet.f8e.configuration

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class GetBdkConfigurationServiceMock(
  turbine: (String) -> Turbine<Any>,
  val defaultElectrumServers: ElectrumServers,
) : GetBdkConfigurationService {
  val getConfigurationCalls = turbine("getConfiguration calls")
  var getConfigurationResult: Result<ElectrumServers, NetworkingError> =
    Ok(defaultElectrumServers)

  fun reset() {
    getConfigurationResult = Ok(defaultElectrumServers)
  }

  override suspend fun getConfiguration(
    f8eEnvironment: F8eEnvironment,
  ): Result<ElectrumServers, NetworkingError> {
    getConfigurationCalls += Unit
    return getConfigurationResult
  }
}
