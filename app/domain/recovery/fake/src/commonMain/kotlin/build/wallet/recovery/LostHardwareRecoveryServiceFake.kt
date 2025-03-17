package build.wallet.recovery

import build.wallet.bitkey.f8e.FullAccountId
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class LostHardwareRecoveryServiceFake : LostHardwareRecoveryService {
  var cancelResult: Result<Unit, CancelDelayNotifyRecoveryError> = Ok(Unit)

  override suspend fun cancelRecovery(
    accountId: FullAccountId,
  ): Result<Unit, CancelDelayNotifyRecoveryError> {
    return cancelResult
  }

  fun reset() {
    cancelResult = Ok(Unit)
  }
}
