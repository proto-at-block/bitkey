package build.wallet.recovery.socrec

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.debug.DebugOptionsService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.di.Impl
import build.wallet.f8e.socrec.SocRecF8eClient
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class SocRecF8eClientProviderImpl(
  private val accountService: AccountService,
  private val debugOptionsService: DebugOptionsService,
  @Fake private val socRecF8eClientFake: SocRecF8eClient,
  @Impl private val socRecF8eClientImpl: SocRecF8eClient,
) : SocRecF8eClientProvider {
  private suspend fun isUsingSocRecFakes(): Boolean {
    return accountService
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
      socRecF8eClientFake
    } else {
      socRecF8eClientImpl
    }
  }
}
