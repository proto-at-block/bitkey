package build.wallet.recovery

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.db.DbTransactionError
import build.wallet.f8e.recovery.ServerRecovery
import build.wallet.recovery.Recovery.NoActiveRecovery
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class RecoveryDaoMock(
  turbine: (name: String) -> Turbine<Any>,
) : RecoveryDao {
  private val recoveryState = MutableStateFlow<Result<Recovery, Error>>(Ok(NoActiveRecovery))

  var recovery: Recovery = NoActiveRecovery
    set(value) {
      field = value
      recoveryState.value = Ok(value)
    }

  override fun activeRecovery(): Flow<Result<Recovery, Error>> = recoveryState

  val setActiveServerRecoveryCalls = turbine("setActiveServerRecoveryCalls calls")

  override suspend fun setActiveServerRecovery(
    activeServerRecovery: ServerRecovery?,
  ): Result<Unit, Error> {
    setActiveServerRecoveryCalls += Unit
    return Ok(Unit)
  }

  var clearCalls = turbine("clear recovery table calls")
  var clearCallResult: Result<Unit, Error> = Ok(Unit)

  override suspend fun clear(): Result<Unit, Error> {
    clearCalls += Unit
    return clearCallResult
  }

  val setLocalRecoveryProgressCalls = turbine("setLocalRecoveryProgress calls")
  var setLocalRecoveryProgressResult: Result<Unit, Error> = Ok(Unit)

  override suspend fun setLocalRecoveryProgress(
    progress: LocalRecoveryAttemptProgress,
  ): Result<Unit, Error> {
    setLocalRecoveryProgressCalls += progress
    return setLocalRecoveryProgressResult
  }

  var setLocalRecoveryPresent: Result<Boolean, DbTransactionError> = Ok(false)

  override suspend fun isLocalRecoveryPresent(): Result<Boolean, DbTransactionError> {
    return setLocalRecoveryPresent
  }

  fun reset() {
    recovery = NoActiveRecovery
    clearCallResult = Ok(Unit)
    setLocalRecoveryProgressResult = Ok(Unit)
  }
}
