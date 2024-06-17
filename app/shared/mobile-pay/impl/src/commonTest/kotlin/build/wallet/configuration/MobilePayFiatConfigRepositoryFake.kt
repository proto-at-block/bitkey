package build.wallet.configuration

import build.wallet.f8e.F8eEnvironment
import build.wallet.money.currency.USD
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class MobilePayFiatConfigRepositoryFake : MobilePayFiatConfigRepository {
  override val configs = MutableStateFlow(
    mapOf(USD to MobilePayFiatConfig.USD)
  )

  override suspend fun fetchAndUpdateConfigs(f8eEnvironment: F8eEnvironment): Result<Unit, Error> {
    // noop
    return Ok(Unit)
  }

  fun reset() {
    configs.value = mapOf(USD to MobilePayFiatConfig.USD)
  }
}
