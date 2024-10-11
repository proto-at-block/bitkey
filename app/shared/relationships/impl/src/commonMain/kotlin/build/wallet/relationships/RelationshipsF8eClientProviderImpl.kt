package build.wallet.relationships

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.debug.DebugOptionsService
import build.wallet.f8e.relationships.RelationshipsF8eClient
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.first

class RelationshipsF8eClientProviderImpl(
  private val accountService: AccountService,
  private val debugOptionsService: DebugOptionsService,
  private val relationshipsFake: RelationshipsF8eClient,
  private val relationshipsF8eClient: RelationshipsF8eClient,
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
      relationshipsFake
    } else {
      relationshipsF8eClient
    }
  }
}
