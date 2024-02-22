package build.wallet.money.display

import app.cash.turbine.Turbine
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BitcoinDisplayPreferenceRepositoryMock(
  turbine: ((String) -> Turbine<Any>)? = null,
) : BitcoinDisplayPreferenceRepository {
  var internalBitcoinDisplayUnit = MutableStateFlow(BitcoinDisplayUnit.Satoshi)
  override val bitcoinDisplayUnit: StateFlow<BitcoinDisplayUnit>
    get() = internalBitcoinDisplayUnit

  val launchSyncCalls = turbine?.invoke("BitcoinDisplayPreferenceRepositoryMock launchSync calls")

  override fun launchSync(scope: CoroutineScope) {
    launchSyncCalls?.add(Unit)
  }

  val setBitcoinDisplayUnitCalls = turbine?.invoke("setBitcoinDisplayUnit calls")

  override suspend fun setBitcoinDisplayUnit(bitcoinDisplayUnit: BitcoinDisplayUnit) {
    setBitcoinDisplayUnitCalls?.add(bitcoinDisplayUnit)
    internalBitcoinDisplayUnit.value = bitcoinDisplayUnit
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
