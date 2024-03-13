package build.wallet.recovery.socrec

import build.wallet.account.AccountRepository
import build.wallet.account.AccountStatus
import build.wallet.f8e.socrec.SocialRecoveryService
import build.wallet.keybox.config.TemplateFullAccountConfigDao
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.first

class SocialRecoveryServiceProviderImpl(
  private val accountRepository: AccountRepository,
  private val templateFullAccountConfigDao: TemplateFullAccountConfigDao,
  private val socRecFake: SocialRecoveryService,
  private val socRecService: SocialRecoveryService,
) : SocialRecoveryServiceProvider {
  private suspend fun isUsingSocRecFakes(): Boolean {
    return accountRepository
      .accountStatus()
      .first()
      .map { status ->
        when (status) {
          is AccountStatus.ActiveAccount ->
            status.account.config.isUsingSocRecFakes
          is AccountStatus.OnboardingAccount ->
            status.account.config.isUsingSocRecFakes
          is AccountStatus.LiteAccountUpgradingToFullAccount ->
            status.account.config.isUsingSocRecFakes

          is AccountStatus.NoAccount -> {
            templateFullAccountConfigDao.config().first().get()?.isUsingSocRecFakes ?: false
          }
        }
      }.get() ?: false
  }

  override suspend fun get(): SocialRecoveryService {
    return if (isUsingSocRecFakes()) {
      socRecFake
    } else {
      socRecService
    }
  }
}
