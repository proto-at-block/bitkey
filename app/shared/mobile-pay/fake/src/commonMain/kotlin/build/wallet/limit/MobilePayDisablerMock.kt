package build.wallet.limit

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

class MobilePayDisablerMock(
  turbine: (name: String) -> Turbine<Any>,
) : MobilePayDisabler {
  val disableCalls = turbine("disable mobile pay calls")
  lateinit var disableResult: Result<Unit, Unit>

  override suspend fun disable(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<Unit, Unit> {
    disableCalls += Unit
    return disableResult
  }
}
