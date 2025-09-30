package build.wallet.platform.links

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class AppStoreUrlProviderImpl : AppStoreUrlProvider {
  override fun getAppStoreUrl(): String =
    "https://play.google.com/store/apps/details?id=world.bitkey.app"
}
