package build.wallet.recovery.socrec

import bitkey.account.AccountConfigService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.di.Impl
import build.wallet.f8e.socrec.SocRecF8eClient

@BitkeyInject(AppScope::class)
class SocRecF8eClientProviderImpl(
  private val accountConfigService: AccountConfigService,
  @Fake private val socRecF8eClientFake: SocRecF8eClient,
  @Impl private val socRecF8eClientImpl: SocRecF8eClient,
) : SocRecF8eClientProvider {
  override fun get(): SocRecF8eClient {
    return if (accountConfigService.activeOrDefaultConfig().value.isUsingSocRecFakes) {
      socRecF8eClientFake
    } else {
      socRecF8eClientImpl
    }
  }
}
