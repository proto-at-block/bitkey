package build.wallet.f8e.demo

import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

interface DemoModeService {
  suspend fun initiateDemoMode(
    f8eEnvironment: F8eEnvironment,
    code: String,
  ): Result<Unit, Error>
}
