package build.wallet.limit

import build.wallet.bitkey.account.FullAccount
import build.wallet.f8e.mobilepay.MobilePaySpendingLimitService
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError

class MobilePayDisablerImpl(
  private val spendingLimitDao: SpendingLimitDao,
  private val spendingLimitService: MobilePaySpendingLimitService,
) : MobilePayDisabler {
  override suspend fun disable(account: FullAccount): Result<Unit, Unit> =
    binding {
      // TODO (W-4166): Currently, if the F8e request fails, we do not handle the error at all.
      //  We should handle that so F8e/App should _never_ be out of sync
      spendingLimitService.disableMobilePay(account.config.f8eEnvironment, account.accountId)

      spendingLimitDao.disableSpendingLimit()
        .mapError { }
        .bind()
    }
}
