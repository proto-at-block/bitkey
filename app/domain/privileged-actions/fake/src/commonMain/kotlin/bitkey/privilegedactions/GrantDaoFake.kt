package bitkey.privilegedactions

import build.wallet.db.DbError
import build.wallet.db.DbQueryError
import build.wallet.db.DbTransactionError
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class GrantDaoFake(
  private val clock: Clock = ClockFake(),
) : GrantDao {
  private data class GrantRecord(
    val grant: Grant,
    val action: GrantAction,
    val createdAt: Instant,
    val delivered: Boolean = false,
  )

  private val grants = MutableStateFlow<Map<GrantAction, GrantRecord>>(emptyMap())

  var shouldFailOnSave: Boolean = false

  override suspend fun saveGrant(grant: Grant): Result<Unit, DbError> {
    if (shouldFailOnSave) {
      return Err(
        DbTransactionError(
          cause = RuntimeException("Simulated save failure"),
          message = "Simulated save failure"
        )
      )
    }

    val action = grant.getGrantAction()
    val record = GrantRecord(
      grant = grant,
      action = action,
      createdAt = clock.now()
    )
    grants.value = grants.value + (action to record)
    return Ok(Unit)
  }

  override suspend fun getGrantByAction(action: GrantAction): Result<Grant?, DbError> {
    val grant = grants.value[action]?.grant
    return Ok(grant)
  }

  override suspend fun deleteGrantByAction(action: GrantAction): Result<Unit, DbError> {
    grants.value = grants.value - action
    return Ok(Unit)
  }

  override fun grantByAction(action: GrantAction): Flow<Grant?> {
    return grants.map { grantsMap -> grantsMap[action]?.grant }
  }

  override suspend fun markAsDelivered(action: GrantAction): Result<Unit, DbError> {
    val currentRecord = grants.value[action]
    return currentRecord?.let { record ->
      val updatedRecord = record.copy(delivered = true)
      grants.value = grants.value + (action to updatedRecord)
      Ok(Unit)
    } ?: Err(DbQueryError(cause = null, message = "Grant not found for action: ${action.name}"))
  }

  override suspend fun getDeliveredStatus(action: GrantAction): Result<Boolean, DbError> {
    val record = grants.value[action]
    return record?.let {
      Ok(it.delivered)
    } ?: Err(DbQueryError(cause = null, message = "Grant not found for action: ${action.name}"))
  }

  fun reset() {
    grants.value = emptyMap()
    shouldFailOnSave = false
  }
}
