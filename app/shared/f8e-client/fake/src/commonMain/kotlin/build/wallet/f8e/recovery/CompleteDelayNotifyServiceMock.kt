package build.wallet.f8e.recovery

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CompleteDelayNotifyServiceMock(
  turbine: (String) -> Turbine<Any>,
) : CompleteDelayNotifyService {
  val completeRecoveryCalls = turbine("complete delay notify recovery calls")

  override suspend fun complete(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    challenge: String,
    appSignature: String,
    hardwareSignature: String,
  ): Result<Unit, NetworkingError> {
    completeRecoveryCalls += Unit
    return Ok(Unit)
  }
}
