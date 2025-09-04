package build.wallet.recovery

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import bitkey.recovery.RecoveryStatusService
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class RecoveryStatusServiceMock(
  val recovery: Recovery = Recovery.NoActiveRecovery,
  turbine: (String) -> Turbine<Any>,
) : RecoveryStatusService {
  val recoveryStatus = MutableStateFlow<Result<Recovery, Error>>(Ok(recovery))
  val clearCalls = turbine("clear recovery syncer calls")
  val setLocalRecoveryProgressCalls = turbine("set local recovery progress calls")
  var setLocalRecoveryProgressResult: Result<Unit, Error> = Ok(Unit)
  var clearCallResult: Result<Unit, Error> = Ok(Unit)

  override fun status(): Flow<Result<Recovery, Error>> {
    return recoveryStatus
  }

  override suspend fun clear(): Result<Unit, Error> {
    clearCalls += Unit
    return clearCallResult
  }

  override suspend fun setLocalRecoveryProgress(
    progress: LocalRecoveryAttemptProgress,
  ): Result<Unit, Error> {
    setLocalRecoveryProgressCalls += progress
    return setLocalRecoveryProgressResult
  }

  fun reset() {
    recoveryStatus.value = Ok(recovery)
    setLocalRecoveryProgressResult = Ok(Unit)
    clearCallResult = Ok(Unit)
  }
}
