package build.wallet.limit

import build.wallet.bitkey.keybox.Keybox
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.mobilepay.MobilePaySpendingLimitService
import build.wallet.limit.MobilePayLimitSetter.SetMobilePayLimitError
import build.wallet.limit.MobilePayLimitSetter.SetMobilePayLimitError.F8eFailedToSaveLimit
import build.wallet.limit.MobilePayLimitSetter.SetMobilePayLimitError.FailedToUpdateLocalLimitState
import build.wallet.logging.logFailure
import build.wallet.logging.logNetworkFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError

class MobilePayLimitSetterImpl(
  private val mobilePaySpendingLimitService: MobilePaySpendingLimitService,
  private val spendingLimitDao: SpendingLimitDao,
) : MobilePayLimitSetter {
  override suspend fun setLimit(
    keybox: Keybox,
    spendingLimit: SpendingLimit,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, SetMobilePayLimitError> {
    return binding {
      mobilePaySpendingLimitService
        .setSpendingLimit(
          fullAccountId = keybox.fullAccountId,
          f8eEnvironment = keybox.config.f8eEnvironment,
          limit = spendingLimit,
          hwFactorProofOfPossession = hwFactorProofOfPossession
        )
        .logNetworkFailure { "Unable to save limit to the backend" }
        .mapError { F8eFailedToSaveLimit(it) }
        .bind()

      spendingLimitDao
        .saveAndSetSpendingLimit(
          limit = spendingLimit
        )
        .logFailure { "Failed to update local limit database" }
        .mapError { FailedToUpdateLocalLimitState(it) }
        .bind()
    }
  }
}
