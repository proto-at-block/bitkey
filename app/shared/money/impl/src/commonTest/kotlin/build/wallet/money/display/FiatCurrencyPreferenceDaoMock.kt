package build.wallet.money.display

import app.cash.turbine.Turbine
import build.wallet.db.DbError
import build.wallet.money.currency.FiatCurrency
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableSharedFlow

class FiatCurrencyPreferenceDaoMock(
  turbine: (String) -> Turbine<Any>,
) : FiatCurrencyPreferenceDao {
  var fiatCurrencyPreferenceFlow = MutableSharedFlow<FiatCurrency>()

  override fun fiatCurrencyPreference() = fiatCurrencyPreferenceFlow

  val setCurrencyPreferenceCalls = turbine("setFiatCurrencyPreference calls")
  var setCurrencyPreferenceResult = Ok(Unit)

  override suspend fun setFiatCurrencyPreference(
    fiatCurrency: FiatCurrency,
  ): Result<Unit, DbError> {
    setCurrencyPreferenceCalls.add(fiatCurrency)
    return setCurrencyPreferenceResult
  }

  val clearCalls = turbine("clear fiatCurrencyPreference calls")
  var clearResult = Ok(Unit)

  override suspend fun clear(): Result<Unit, DbError> {
    clearCalls.add(Unit)
    return clearResult
  }

  fun reset() {
    fiatCurrencyPreferenceFlow = MutableSharedFlow()
    setCurrencyPreferenceResult = Ok(Unit)
    clearResult = Ok(Unit)
  }
}
