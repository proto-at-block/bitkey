package build.wallet.recovery

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.recovery.RecoverySyncer.SyncError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration

class RecoverySyncerMock(
  val recovery: Recovery = Recovery.NoActiveRecovery,
  turbine: (String) -> Turbine<Any>,
) : RecoverySyncer {
  val recoveryStatus = MutableStateFlow<Result<Recovery, Error>>(Ok(recovery))
  val clearCalls = turbine("clear recovery syncer calls")
  val setLocalRecoveryProgressCalls = turbine("set local recovery progress calls")
  var clearCallResult: Result<Unit, Error> = Ok(Unit)

  override suspend fun performSync(fullAccountId: FullAccountId): Result<Unit, SyncError> = Ok(Unit)

  override fun launchSync(
    scope: CoroutineScope,
    syncFrequency: Duration,
    fullAccountId: FullAccountId,
  ) {
    // no-op
  }

  override fun recoveryStatus(): Flow<Result<Recovery, Error>> {
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
    return Ok(Unit)
  }

  fun reset() {
    recoveryStatus.value = Ok(recovery)
    clearCallResult = Ok(Unit)
  }
}
