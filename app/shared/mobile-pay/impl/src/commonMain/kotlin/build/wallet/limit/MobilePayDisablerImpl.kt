package build.wallet.limit

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.mobilepay.MobilePaySpendingLimitService
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError

class MobilePayDisablerImpl(
  private val spendingLimitDao: SpendingLimitDao,
  private val spendingLimitService: MobilePaySpendingLimitService,
) : MobilePayDisabler {
  override suspend fun disable(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<Unit, Unit> =
    binding {
      // TODO (W-4166): Currently, if the F8e request fails, we do not handle the error at all.
      //  We should handle that so F8e/App should _never_ be out of sync
      spendingLimitService.disableMobilePay(f8eEnvironment, fullAccountId)

      spendingLimitDao.disableSpendingLimit()
        .mapError { }
        .bind()
    }
}
