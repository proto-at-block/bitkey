package build.wallet.limit

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.account.FullAccount
import com.github.michaelbull.result.Result

class MobilePayDisablerMock(
  turbine: (name: String) -> Turbine<Any>,
) : MobilePayDisabler {
  val disableCalls = turbine("disable mobile pay calls")
  lateinit var disableResult: Result<Unit, Unit>

  override suspend fun disable(account: FullAccount): Result<Unit, Unit> {
    disableCalls += Unit
    return disableResult
  }
}
