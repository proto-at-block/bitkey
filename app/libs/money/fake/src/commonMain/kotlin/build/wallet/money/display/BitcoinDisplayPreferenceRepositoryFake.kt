package build.wallet.money.display

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class BitcoinDisplayPreferenceRepositoryFake(
  private val defaultUnit: BitcoinDisplayUnit = BitcoinDisplayUnit.Bitcoin,
) : BitcoinDisplayPreferenceRepository {
  override val bitcoinDisplayUnit = MutableStateFlow(defaultUnit)

  override suspend fun setBitcoinDisplayUnit(
    bitcoinDisplayUnit: BitcoinDisplayUnit,
  ): Result<Unit, Error> {
    this.bitcoinDisplayUnit.value = bitcoinDisplayUnit
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    bitcoinDisplayUnit.value = defaultUnit
    return Ok(Unit)
  }
}
