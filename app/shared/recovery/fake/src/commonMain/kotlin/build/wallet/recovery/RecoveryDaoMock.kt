package build.wallet.recovery

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.db.DbError
import build.wallet.f8e.recovery.ServerRecovery
import build.wallet.recovery.Recovery.NoActiveRecovery
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class RecoveryDaoMock(
  turbine: (name: String) -> Turbine<Unit>,
) : RecoveryDao {
  var recovery: Recovery = NoActiveRecovery

  override fun activeRecovery(): Flow<Result<Recovery, DbError>> {
    return MutableStateFlow<Result<Recovery, DbError>>(
      Ok(recovery)
    )
  }

  val setActiveServerRecoveryCalls = turbine("setActiveRecovery calls")

  override suspend fun setActiveServerRecovery(
    activeServerRecovery: ServerRecovery?,
  ): Result<Unit, DbError> {
    setActiveServerRecoveryCalls += Unit
    return Ok(Unit)
  }

  var clearCalls = turbine("clear recovery table calls")
  var clearCallResult: Result<Unit, DbError> = Ok(Unit)

  override suspend fun clear(): Result<Unit, DbError> {
    clearCalls += Unit
    return clearCallResult
  }

  val setLocalRecoveryProgressCalls = turbine("setLocalRecoveryProgressCalls")

  override suspend fun setLocalRecoveryProgress(
    progress: LocalRecoveryAttemptProgress,
  ): Result<Unit, DbError> {
    setLocalRecoveryProgressCalls += Unit
    return Ok(Unit)
  }

  fun reset() {
    clearCallResult = Ok(Unit)
  }
}
