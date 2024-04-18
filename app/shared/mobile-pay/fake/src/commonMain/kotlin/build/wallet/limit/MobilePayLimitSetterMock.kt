package build.wallet.limit

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.account.FullAccount
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.limit.MobilePayLimitSetter.SetMobilePayLimitError
import com.github.michaelbull.result.Result

class MobilePayLimitSetterMock(
  turbine: (name: String) -> Turbine<Any>,
) : MobilePayLimitSetter {
  lateinit var setLimitResult: Result<Unit, SetMobilePayLimitError>
  val setLimitCalls = turbine("set spending limit calls")

  override suspend fun setLimit(
    account: FullAccount,
    spendingLimit: SpendingLimit,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, SetMobilePayLimitError> {
    setLimitCalls += spendingLimit
    return setLimitResult
  }
}
