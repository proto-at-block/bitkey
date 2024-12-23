package build.wallet.relationships

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.debug.DebugOptionsService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.di.Impl
import build.wallet.f8e.relationships.RelationshipsF8eClient
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class RelationshipsF8eClientProviderImpl(
  private val accountService: AccountService,
  private val debugOptionsService: DebugOptionsService,
  @Impl private val relationshipsF8eClientImpl: RelationshipsF8eClient,
  @Fake private val relationshipsF8eClientFake: RelationshipsF8eClient,
) : RelationshipsF8eClientProvider {
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

  override suspend fun get(): RelationshipsF8eClient {
    return if (isUsingSocRecFakes()) {
      relationshipsF8eClientFake
    } else {
      relationshipsF8eClientImpl
    }
  }
}
