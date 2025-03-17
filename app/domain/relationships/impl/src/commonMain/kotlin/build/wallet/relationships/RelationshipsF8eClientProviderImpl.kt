package build.wallet.relationships

import bitkey.account.AccountConfigService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.di.Impl
import build.wallet.f8e.relationships.RelationshipsF8eClient

@BitkeyInject(AppScope::class)
class RelationshipsF8eClientProviderImpl(
  private val accountConfigService: AccountConfigService,
  @Impl private val relationshipsF8eClientImpl: RelationshipsF8eClient,
  @Fake private val relationshipsF8eClientFake: RelationshipsF8eClient,
) : RelationshipsF8eClientProvider {
  override fun get(): RelationshipsF8eClient {
    return if (accountConfigService.activeOrDefaultConfig().value.isUsingSocRecFakes) {
      relationshipsF8eClientFake
    } else {
      relationshipsF8eClientImpl
    }
  }
}
