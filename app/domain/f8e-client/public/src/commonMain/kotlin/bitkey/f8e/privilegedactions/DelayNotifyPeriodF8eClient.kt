package bitkey.f8e.privilegedactions

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

interface DelayNotifyPeriodF8eClient {
  suspend fun getDelayNotifyPeriod(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<Int, NetworkingError>

  suspend fun setDelayNotifyPeriod(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    delayPeriodDays: Int,
    proof: PrivilegedActionProof?,
  ): Result<Int, NetworkingError>
}
