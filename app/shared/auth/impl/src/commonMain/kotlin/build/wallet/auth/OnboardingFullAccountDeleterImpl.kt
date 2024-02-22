package build.wallet.auth

import build.wallet.account.AccountRepository
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.onboarding.DeleteOnboardingFullAccountService
import build.wallet.keybox.KeyboxDao
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding

class OnboardingFullAccountDeleterImpl(
  private val accountRepository: AccountRepository,
  private val keyboxDao: KeyboxDao,
  private val deleteOnboardingFullAccountService: DeleteOnboardingFullAccountService,
) : OnboardingFullAccountDeleter {
  override suspend fun deleteAccount(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error> =
    binding {
      deleteOnboardingFullAccountService.deleteOnboardingFullAccount(
        f8eEnvironment,
        fullAccountId,
        hardwareProofOfPossession
      ).bind()
      accountRepository.clear().bind()
      keyboxDao.clear().bind()
    }
}
