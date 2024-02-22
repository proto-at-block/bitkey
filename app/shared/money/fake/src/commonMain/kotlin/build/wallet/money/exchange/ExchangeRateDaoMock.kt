package build.wallet.money.exchange

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Instant

class ExchangeRateDaoMock(
  turbine: (String) -> Turbine<Any>,
) : ExchangeRateDao {
  val storeExchangeRateCalls = turbine("store current exchange rate calls")
  val storeHistoricalExchangeRateCalls = turbine("store historical exchange rate calls")

  var historicalExchangeRates: Map<Instant, List<ExchangeRate>> = emptyMap()

  var allExchangeRates = MutableStateFlow<List<ExchangeRate>>(emptyList())

  override suspend fun storeExchangeRate(exchangeRate: ExchangeRate): Result<Unit, DbError> {
    storeExchangeRateCalls += exchangeRate
    return Ok(Unit)
  }

  override suspend fun storeHistoricalExchangeRate(
    exchangeRate: ExchangeRate,
    atTime: Instant,
  ): Result<Unit, DbError> {
    storeHistoricalExchangeRateCalls += Pair(exchangeRate, atTime)
    return Ok(Unit)
  }

  override fun allExchangeRates(): Flow<List<ExchangeRate>> = allExchangeRates

  override suspend fun historicalExchangeRatesAtTime(time: Instant): List<ExchangeRate>? =
    historicalExchangeRates[time]

  fun reset() {
    allExchangeRates = MutableStateFlow(emptyList())
    historicalExchangeRates = emptyMap()
  }
}
