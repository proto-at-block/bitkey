package build.wallet.configuration

import build.wallet.db.DbError
import build.wallet.money.currency.FiatCurrency
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class MobilePayFiatConfigDaoFake : MobilePayFiatConfigDao {
  private val configurations = MutableStateFlow(emptyMap<FiatCurrency, MobilePayFiatConfig>())

  override fun allConfigurations(): Flow<Map<FiatCurrency, MobilePayFiatConfig>> {
    return configurations
  }

  override suspend fun storeConfigurations(
    configurations: Map<FiatCurrency, MobilePayFiatConfig>,
  ): Result<Unit, DbError> {
    this.configurations.value = configurations
    return Ok(Unit)
  }

  fun reset() {
    configurations.value = emptyMap()
  }
}
