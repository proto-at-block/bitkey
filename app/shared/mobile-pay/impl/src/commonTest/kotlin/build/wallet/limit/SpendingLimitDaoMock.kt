package build.wallet.limit

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class SpendingLimitDaoMock(
  turbine: (String) -> Turbine<Any>,
) : SpendingLimitDao {
  val clearActiveLimitCalls = turbine("clear active limit calls")
  val removeAllLimitsCalls = turbine("remove all limits calls")
  private val limits = mutableListOf<SpendingLimit>()

  var activeLimitFlow = MutableStateFlow<SpendingLimit?>(null)

  override fun activeSpendingLimit(): Flow<SpendingLimit?> = activeLimitFlow

  override suspend fun mostRecentSpendingLimit(): Result<SpendingLimit?, Error> {
    return Ok(limits.firstOrNull())
  }

  override suspend fun saveAndSetSpendingLimit(limit: SpendingLimit): Result<Unit, Error> {
    limits.add(limit)
    activeLimitFlow.emit(limit)
    return Ok(Unit)
  }

  override suspend fun disableSpendingLimit(): Result<Unit, Error> {
    clearActiveLimitCalls += Unit
    activeLimitFlow.emit(null)
    return Ok(Unit)
  }

  override suspend fun removeAllLimits(): Result<Unit, Error> {
    removeAllLimitsCalls += Unit
    limits.clear()
    return Ok(Unit)
  }

  fun reset() {
    limits.clear()
    activeLimitFlow.value = null
  }
}
