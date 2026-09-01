package bitkey.securitycenter

import bitkey.f8e.privilegedactions.DelayNotifyPeriodF8eClient
import build.wallet.bitkey.account.FullAccount
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.ktor.result.NetworkingError
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest

@BitkeyInject(AppScope::class)
class DelayNotifyConfigurationServiceImpl(
  private val delayNotifyPeriodF8eClient: DelayNotifyPeriodF8eClient,
  private val delayNotifyConfigurationDao: DelayNotifyConfigurationDao,
) : DelayNotifyConfigurationService {
  override fun delayNotifyPeriod(account: FullAccount): Flow<Int?> {
    return delayNotifyConfigurationDao.getDelayNotifyPeriod(account.accountId)
      .flatMapLatest { cachedPeriod ->
        if (cachedPeriod != null) {
          flowOf<Int?>(cachedPeriod)
        } else {
          flow<Int?> {
            emit(null)
            val fetchedPeriod = syncDelayNotifyPeriod(account).get()
            if (fetchedPeriod != null) {
              emit(fetchedPeriod)
            }
          }
        }
      }
      .distinctUntilChanged()
  }

  override suspend fun syncDelayNotifyPeriod(account: FullAccount): Result<Int, NetworkingError> {
    val result = fetchDelayNotifyPeriod(account)
    val fetchedPeriod = result.get()
    if (fetchedPeriod != null) {
      cacheDelayNotifyPeriod(account, fetchedPeriod)
    }
    return result
  }

  override suspend fun setDelayNotifyPeriod(
    account: FullAccount,
    delayPeriodDays: Int,
    proof: PrivilegedActionProof?,
  ): Result<Int, NetworkingError> {
    val result = delayNotifyPeriodF8eClient.setDelayNotifyPeriod(
      f8eEnvironment = account.config.f8eEnvironment,
      fullAccountId = account.accountId,
      delayPeriodDays = delayPeriodDays,
      proof = proof,
    )
      .logFailure { "Failed to set delay notify period" }

    val updatedDays = result.get()
    if (updatedDays != null) {
      cacheDelayNotifyPeriod(account, updatedDays)
    }

    return result
  }

  /**
   * Persists the period to the local cache, swallowing (but logging) persistence errors.
   * The server is the source of truth at this point; a local cache failure should not
   * fail the overall operation or throw past the declared [NetworkingError] result type.
   */
  private suspend fun cacheDelayNotifyPeriod(
    account: FullAccount,
    delayPeriodDays: Int,
  ) {
    catchingResult {
      delayNotifyConfigurationDao.setDelayNotifyPeriod(
        accountId = account.accountId,
        delayPeriodDays = delayPeriodDays
      )
    }.logFailure { "Failed to cache delay notify period locally" }
  }

  private suspend fun fetchDelayNotifyPeriod(account: FullAccount): Result<Int, NetworkingError> {
    return delayNotifyPeriodF8eClient.getDelayNotifyPeriod(
      f8eEnvironment = account.config.f8eEnvironment,
      fullAccountId = account.accountId,
    )
      .logFailure { "Failed to get delay notify period" }
  }
}
