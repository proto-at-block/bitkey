package build.wallet.limit

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.account.FullAccount
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class MobilePayServiceMock(
  turbine: (name: String) -> Turbine<Any>,
) : MobilePayService {
  override val mobilePayData = MutableStateFlow<MobilePayData?>(null)

  val disableCalls = turbine("disable mobile pay calls")
  var disableResult: Result<Unit, Error> = Ok(Unit)

  override suspend fun disable(account: FullAccount): Result<Unit, Error> {
    disableCalls += Unit
    return disableResult
  }

  val deleteLocalCalls = turbine("delete local calls")

  override suspend fun deleteLocal(): Result<Unit, Error> {
    deleteLocalCalls += Unit
    return Ok(Unit)
  }

  val setLimitCalls = turbine("set mobile pay limit calls")

  override suspend fun setLimit(
    account: FullAccount,
    spendingLimit: SpendingLimit,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error> {
    setLimitCalls += spendingLimit
    return Ok(Unit)
  }

  fun reset() {
    disableResult = Ok(Unit)
    mobilePayData.value = null
  }
}
