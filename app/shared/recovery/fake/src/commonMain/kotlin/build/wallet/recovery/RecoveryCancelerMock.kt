package build.wallet.recovery

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class RecoveryCancelerMock(
  val turbine: (String) -> Turbine<Any>,
) : RecoveryCanceler {
  val cancelCalls = turbine("cancel calls")

  var result: Result<Unit, RecoveryCanceler.RecoveryCancelerError> = Ok(Unit)

  override suspend fun cancel(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, RecoveryCanceler.RecoveryCancelerError> {
    cancelCalls += Unit
    return result
  }
}
