package build.wallet.money.display

import app.cash.turbine.Turbine
import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableSharedFlow

class BitcoinDisplayPreferenceDaoMock(
  turbine: (String) -> Turbine<Any>,
) : BitcoinDisplayPreferenceDao {
  var bitcoinDisplayPreferenceFlow = MutableSharedFlow<BitcoinDisplayUnit>()

  override fun bitcoinDisplayPreference() = bitcoinDisplayPreferenceFlow

  val setBitcoinDisplayPreferenceCalls = turbine("setBitcoinDisplayPreference calls")
  var setBitcoinDisplayPreferenceResult = Ok(Unit)

  override suspend fun setBitcoinDisplayPreference(
    unit: BitcoinDisplayUnit,
  ): Result<Unit, DbError> {
    setBitcoinDisplayPreferenceCalls.add(unit)
    return setBitcoinDisplayPreferenceResult
  }

  val clearCalls = turbine("clear bitcoinDisplayPreference calls")
  var clearResult = Ok(Unit)

  override suspend fun clear(): Result<Unit, DbError> {
    clearCalls.add(Unit)
    return clearResult
  }

  fun reset() {
    bitcoinDisplayPreferenceFlow = MutableSharedFlow()
    setBitcoinDisplayPreferenceResult = Ok(Unit)
    clearResult = Ok(Unit)
  }
}
