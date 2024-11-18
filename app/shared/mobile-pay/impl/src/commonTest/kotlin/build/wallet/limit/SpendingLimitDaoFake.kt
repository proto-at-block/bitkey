package build.wallet.limit

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class SpendingLimitDaoFake : SpendingLimitDao {
  private val activeSpendingLimit = MutableStateFlow<SpendingLimit?>(null)

  override fun activeSpendingLimit(): Flow<SpendingLimit?> {
    return activeSpendingLimit
  }

  private val mostRecentSpendingLimit = MutableStateFlow<SpendingLimit?>(null)

  override suspend fun mostRecentSpendingLimit(): Result<SpendingLimit?, Error> {
    return Ok(mostRecentSpendingLimit.value)
  }

  override suspend fun saveAndSetSpendingLimit(limit: SpendingLimit): Result<Unit, Error> {
    activeSpendingLimit.value = limit
    mostRecentSpendingLimit.value = limit
    return Ok(Unit)
  }

  override suspend fun disableSpendingLimit(): Result<Unit, Error> {
    activeSpendingLimit.value = null
    return Ok(Unit)
  }

  override suspend fun removeAllLimits(): Result<Unit, Error> {
    activeSpendingLimit.value = null
    mostRecentSpendingLimit.value = null
    return Ok(Unit)
  }

  fun reset() {
    activeSpendingLimit.value = null
    mostRecentSpendingLimit.value = null
  }
}
