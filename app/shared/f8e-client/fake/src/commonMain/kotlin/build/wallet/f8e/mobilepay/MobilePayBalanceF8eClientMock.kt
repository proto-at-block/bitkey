package build.wallet.f8e.mobilepay

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.limit.MobilePayBalance
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class MobilePayBalanceF8eClientMock(
  turbine: (String) -> Turbine<Any>,
  var mobilePayBalanceResult: Result<MobilePayBalance, MobilePayBalanceFailure>,
) : MobilePayBalanceF8eClient {
  val mobilePayBalanceCalls = turbine("get mobile pay balance")

  constructor(
    turbine: (String) -> Turbine<Any>,
    mobilePayBalance: MobilePayBalance,
  ) : this(
    turbine,
    mobilePayBalanceResult = Ok(mobilePayBalance)
  )

  override suspend fun getMobilePayBalance(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<MobilePayBalance, MobilePayBalanceFailure> {
    mobilePayBalanceCalls += Unit
    return mobilePayBalanceResult
  }

  fun reset(mobilePayBalance: MobilePayBalance) {
    this.mobilePayBalanceResult = Ok(mobilePayBalance)
  }
}
