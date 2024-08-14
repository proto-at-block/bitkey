package build.wallet.recovery.socrec

import build.wallet.account.AccountRepository
import build.wallet.account.AccountStatus
import build.wallet.debug.DebugOptionsService
import build.wallet.f8e.socrec.SocRecF8eClient
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.first

class SocRecF8eClientProviderImpl(
  private val accountRepository: AccountRepository,
  private val debugOptionsService: DebugOptionsService,
  private val socRecFake: SocRecF8eClient,
  private val socRecF8eClient: SocRecF8eClient,
) : SocRecF8eClientProvider {
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
            debugOptionsService.options().first().isUsingSocRecFakes
          }
        }
      }.get() ?: false
  }

  override suspend fun get(): SocRecF8eClient {
    return if (isUsingSocRecFakes()) {
      socRecFake
    } else {
      socRecF8eClient
    }
  }
}
