package build.wallet.recovery

import bitkey.recovery.RecoveryStatusService
import build.wallet.recovery.Recovery.NoActiveRecovery
import com.github.michaelbull.result.Ok
import kotlinx.coroutines.flow.MutableStateFlow

class RecoveryStatusServiceFake(
  initialRecovery: Recovery = NoActiveRecovery,
) : RecoveryStatusService {
  override val status = MutableStateFlow<Recovery>(initialRecovery)

  override suspend fun clear() = Ok(Unit)

  override suspend fun setLocalRecoveryProgress(
    progress: LocalRecoveryAttemptProgress,
  ) = Ok(Unit)

  fun reset() {
    status.value = NoActiveRecovery
  }
}
