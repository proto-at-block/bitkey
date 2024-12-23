package build.wallet.platform.links

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class DeepLinkHandlerImpl : DeepLinkHandler {
  override fun openDeeplink(
    url: String,
    appRestrictions: AppRestrictions?,
  ): OpenDeeplinkResult {
    TODO("Implement for JVM if needed")
  }
}
