package build.wallet.platform.settings

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class LocaleCurrencyCodeProviderImpl : LocaleCurrencyCodeProvider {
  override fun localeCurrencyCode(): String {
    return "USD"
  }
}
