package build.wallet.f8e.recovery

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class GetDelayNotifyRecoveryStatusServiceMock(
  var activeRecovery: ServerRecovery? = null,
) : GetDelayNotifyRecoveryStatusService {
  override suspend fun getStatus(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<ServerRecovery?, NetworkingError> {
    return when (val recovery = activeRecovery) {
      null -> Ok(null)
      else -> Ok(recovery)
    }
  }

  fun reset() {
    activeRecovery = null
  }
}
