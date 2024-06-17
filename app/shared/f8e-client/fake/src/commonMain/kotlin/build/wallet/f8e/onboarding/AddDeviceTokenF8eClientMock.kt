package build.wallet.f8e.onboarding

import app.cash.turbine.Turbine
import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.platform.config.TouchpointPlatform
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class AddDeviceTokenF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : AddDeviceTokenF8eClient {
  val addCalls = turbine("add calls")
  var addResult: Result<Unit, NetworkingError> = Ok(Unit)

  data class AddParams(
    val f8eEnvironment: F8eEnvironment,
    val fullAccountId: FullAccountId,
    val token: String,
    val touchpointPlatform: TouchpointPlatform,
  )

  override suspend fun add(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    token: String,
    touchpointPlatform: TouchpointPlatform,
    authTokenScope: AuthTokenScope,
  ): Result<Unit, NetworkingError> {
    addCalls.add(AddParams(f8eEnvironment, fullAccountId, token, touchpointPlatform))
    return addResult
  }

  fun reset() {
    addResult = Ok(Unit)
  }
}
