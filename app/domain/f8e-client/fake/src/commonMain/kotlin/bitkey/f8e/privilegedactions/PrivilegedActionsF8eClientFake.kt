package bitkey.f8e.privilegedactions

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.encrypt.WsmVerifierMock
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.F8eHttpClient
import build.wallet.f8e.client.F8eHttpClientMock
import com.github.michaelbull.result.Result

open class PrivilegedActionsF8eClientFake<Req, Res>(
  turbine: (name: String) -> Turbine<Any>,
) : DirectPrivilegedActionsF8eClient<Req, Res> {
  override val f8eHttpClient: F8eHttpClient = F8eHttpClientMock(WsmVerifierMock())

  val getPrivilegedActionInstancesCalls = turbine("getPrivilegedActionInstances calls")
  var getPrivilegedActionInstancesResult: Result<List<PrivilegedActionInstance>, Throwable>? = null
  val createPrivilegedActionCalls = turbine("createPrivilegedAction calls")
  var createPrivilegedActionResult: Result<PrivilegedActionInstance, Throwable>? = null
  val continuePrivilegedActionCalls = turbine("continuePrivilegedAction calls")
  var continuePrivilegedActionResult: Result<Res, Throwable>? = null

  override suspend fun getPrivilegedActionInstances(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<List<PrivilegedActionInstance>, Throwable> {
    getPrivilegedActionInstancesCalls.add(Pair(f8eEnvironment, fullAccountId))
    return getPrivilegedActionInstancesResult ?: error("getPrivilegedActionInstancesResult not set")
  }

  override suspend fun createPrivilegedAction(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    request: Req,
  ): Result<PrivilegedActionInstance, Throwable> {
    createPrivilegedActionCalls.add(request as Any)
    return createPrivilegedActionResult ?: error("createPrivilegedAction not set")
  }

  override suspend fun continuePrivilegedAction(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    request: ContinuePrivilegedActionRequest,
  ): Result<Res, Throwable> {
    continuePrivilegedActionCalls.add(request)
    return continuePrivilegedActionResult ?: error("continueFingerprintResetResult not set")
  }
}
