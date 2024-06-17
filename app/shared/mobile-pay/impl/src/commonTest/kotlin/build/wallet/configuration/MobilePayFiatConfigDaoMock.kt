package build.wallet.configuration

import app.cash.turbine.Turbine
import build.wallet.db.DbError
import build.wallet.money.currency.FiatCurrency
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableSharedFlow

class MobilePayFiatConfigDaoMock(
  turbine: (String) -> Turbine<Any>,
) : MobilePayFiatConfigDao {
  var allConfigurationsFlow = MutableSharedFlow<Map<FiatCurrency, MobilePayFiatConfig>>()

  override fun allConfigurations() = allConfigurationsFlow

  val storeConfigurationCalls = turbine("storeConfiguration calls")

  override suspend fun storeConfigurations(
    configurations: Map<FiatCurrency, MobilePayFiatConfig>,
  ): Result<Unit, DbError> {
    storeConfigurationCalls.add(configurations)
    return Ok(Unit)
  }

  fun reset() {
    allConfigurationsFlow = MutableSharedFlow()
  }
}
