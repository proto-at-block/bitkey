package build.wallet.platform.links

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class AppStoreUrlProviderImpl : AppStoreUrlProvider {
  override fun getAppStoreUrl(): String = "https://apps.apple.com/app/id6476990471"
}
