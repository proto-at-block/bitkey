package bitkey.onboarding

import bitkey.account.AccountConfigService
import build.wallet.account.AccountService
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.onboarding.DeleteOnboardingFullAccountF8eClient
import build.wallet.keybox.KeyboxDao
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

@BitkeyInject(AppScope::class)
class DeleteFullAccountServiceImpl(
  private val accountService: AccountService,
  private val keyboxDao: KeyboxDao,
  private val deleteOnboardingFullAccountF8eClient: DeleteOnboardingFullAccountF8eClient,
  private val accountConfigService: AccountConfigService,
) : DeleteFullAccountService {
  override suspend fun deleteAccount(
    fullAccountId: FullAccountId,
    hardwareProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error> =
    coroutineBinding {
      val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
      deleteOnboardingFullAccountF8eClient.deleteOnboardingFullAccount(
        f8eEnvironment = f8eEnvironment,
        fullAccountId = fullAccountId,
        hwFactorProofOfPossession = hardwareProofOfPossession
      ).bind()
      accountService.clear().bind()
      keyboxDao.clear().bind()
    }
}
