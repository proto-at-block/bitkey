package bitkey.securitycenter

import build.wallet.bitkey.account.FullAccount
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.ktor.result.NetworkingError
import build.wallet.worker.AppWorker
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface DelayNotifyConfigurationService {
  fun delayNotifyPeriod(account: FullAccount): Flow<Int?>

  suspend fun syncDelayNotifyPeriod(account: FullAccount): Result<Int, NetworkingError>

  suspend fun setDelayNotifyPeriod(
    account: FullAccount,
    delayPeriodDays: Int,
    proof: PrivilegedActionProof?,
  ): Result<Int, NetworkingError>
}

/**
 * Periodically syncs the account's delay notify period from F8e into local cache.
 */
interface DelayNotifyConfigurationSyncWorker : AppWorker
