package build.wallet.limit

import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_MOBILE_TRANSACTIONS_DISABLED
import build.wallet.analytics.v1.Action.ACTION_APP_MOBILE_TRANSACTIONS_ENABLED
import build.wallet.bitkey.account.FullAccount
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.mobilepay.MobilePaySpendingLimitF8eClient
import build.wallet.logging.logFailure
import build.wallet.logging.logNetworkFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.Flow

class MobilePayServiceImpl(
  private val eventTracker: EventTracker,
  private val spendingLimitDao: SpendingLimitDao,
  private val spendingLimitF8eClient: MobilePaySpendingLimitF8eClient,
  private val mobilePayStatusRepository: MobilePayStatusRepository,
) : MobilePayService {
  override suspend fun deleteLocal(): Result<Unit, Error> {
    return spendingLimitDao.removeAllLimits()
  }

  override suspend fun refreshStatus() {
    mobilePayStatusRepository.refreshStatus()
  }

  override fun status(account: FullAccount): Flow<MobilePayStatus> {
    return mobilePayStatusRepository.status(account)
  }

  override suspend fun disable(account: FullAccount): Result<Unit, Error> =
    coroutineBinding {
      // TODO (W-4166): Currently, if the F8e request fails, we do not handle the error at all.
      //  We should handle that so F8e/App should _never_ be out of sync
      spendingLimitF8eClient.disableMobilePay(account.config.f8eEnvironment, account.accountId)

      spendingLimitDao.disableSpendingLimit()
        .bind()

      eventTracker.track(ACTION_APP_MOBILE_TRANSACTIONS_DISABLED)
    }

  override suspend fun setLimit(
    account: FullAccount,
    spendingLimit: SpendingLimit,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error> {
    return coroutineBinding {
      spendingLimitF8eClient
        .setSpendingLimit(
          fullAccountId = account.accountId,
          f8eEnvironment = account.config.f8eEnvironment,
          limit = spendingLimit,
          hwFactorProofOfPossession = hwFactorProofOfPossession
        )
        .logNetworkFailure { "Unable to save limit to the backend" }
        .bind()

      // save new active limit to local database
      spendingLimitDao
        .saveAndSetSpendingLimit(limit = spendingLimit)
        .logFailure { "Failed to update local limit database" }
        .bind()

      eventTracker.track(ACTION_APP_MOBILE_TRANSACTIONS_ENABLED)
    }
  }
}
