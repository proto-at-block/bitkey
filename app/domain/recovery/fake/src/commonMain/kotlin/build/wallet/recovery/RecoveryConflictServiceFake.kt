package build.wallet.recovery

import build.wallet.bitkey.f8e.FullAccountId
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class RecoveryConflictServiceFake : RecoveryConflictService {
  var cancelResult: Result<Unit, RecoveryConflictServiceError> = Ok(Unit)

  var latestCancelAccountId: FullAccountId? = null

  override suspend fun cancelRecoveryConflict(
    fullAccountId: FullAccountId,
  ): Result<Unit, RecoveryConflictServiceError> {
    latestCancelAccountId = fullAccountId
    return cancelResult
  }

  fun reset() {
    cancelResult = Ok(Unit)
    latestCancelAccountId = null
  }
}
