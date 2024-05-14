package build.wallet.money.display

import app.cash.turbine.Turbine
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BitcoinDisplayPreferenceRepositoryMock(
  turbine: ((String) -> Turbine<Any>)? = null,
) : BitcoinDisplayPreferenceRepository {
  var internalBitcoinDisplayUnit = MutableStateFlow(BitcoinDisplayUnit.Satoshi)
  override val bitcoinDisplayUnit: StateFlow<BitcoinDisplayUnit>
    get() = internalBitcoinDisplayUnit

  val setBitcoinDisplayUnitCalls = turbine?.invoke("setBitcoinDisplayUnit calls")

  override suspend fun setBitcoinDisplayUnit(
    bitcoinDisplayUnit: BitcoinDisplayUnit,
  ): Result<Unit, Error> {
    setBitcoinDisplayUnitCalls?.add(bitcoinDisplayUnit)
    internalBitcoinDisplayUnit.value = bitcoinDisplayUnit
    return Ok(Unit)
  }

  val clearCalls = turbine?.invoke("clear BitcoinDisplayPreferenceRepository calls")

  override suspend fun clear(): Result<Unit, Error> {
    clearCalls?.add(Unit)
    return Ok(Unit)
  }

  fun reset() {
    internalBitcoinDisplayUnit.value = BitcoinDisplayUnit.Satoshi
  }
}
