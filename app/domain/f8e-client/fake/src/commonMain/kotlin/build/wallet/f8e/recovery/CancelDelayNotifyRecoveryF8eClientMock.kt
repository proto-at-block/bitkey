package build.wallet.f8e.recovery

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CancelDelayNotifyRecoveryF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : CancelDelayNotifyRecoveryF8eClient {
  val cancelRecoveryCalls = turbine("cancel delay notify recovery calls")

  var cancelResult: Result<Unit, F8eError<CancelDelayNotifyRecoveryErrorCode>> = Ok(Unit)

  override suspend fun cancel(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
  ): Result<Unit, F8eError<CancelDelayNotifyRecoveryErrorCode>> {
    cancelRecoveryCalls += Unit
    return cancelResult
  }

  fun reset() {
    cancelResult = Ok(Unit)
  }
}
