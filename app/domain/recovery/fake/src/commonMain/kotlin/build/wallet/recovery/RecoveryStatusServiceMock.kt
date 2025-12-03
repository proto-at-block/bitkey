package build.wallet.recovery

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import bitkey.recovery.RecoveryStatusService
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RecoveryStatusServiceMock(
  var recovery: Recovery = Recovery.NoActiveRecovery,
  turbine: (String) -> Turbine<Any>,
) : RecoveryStatusService {
  val recoveryStatus = MutableStateFlow(recovery)
  val clearCalls = turbine("clear recovery syncer calls")
  val setLocalRecoveryProgressCalls = turbine("set local recovery progress calls")
  var setLocalRecoveryProgressResult: Result<Unit, Error> = Ok(Unit)
  var clearCallResult: Result<Unit, Error> = Ok(Unit)

  override val status: StateFlow<Recovery> = recoveryStatus

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
    recoveryStatus.value = recovery
    setLocalRecoveryProgressResult = Ok(Unit)
    clearCallResult = Ok(Unit)
  }
}
