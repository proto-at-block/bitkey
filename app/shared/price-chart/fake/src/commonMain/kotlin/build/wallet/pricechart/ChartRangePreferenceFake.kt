package build.wallet.pricechart

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class ChartRangePreferenceFake : ChartRangePreference {
  override val selectedRange = MutableStateFlow(ChartRange.DAY)

  override suspend fun get(): Result<ChartRange, Error> {
    return Ok(selectedRange.value)
  }

  override suspend fun set(scale: ChartRange): Result<Unit, Error> {
    selectedRange.value = scale
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    selectedRange.value = ChartRange.DAY
    return Ok(Unit)
  }
}
