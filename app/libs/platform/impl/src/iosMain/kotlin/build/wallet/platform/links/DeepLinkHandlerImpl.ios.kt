package build.wallet.platform.links

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logWarn
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult.None
import build.wallet.platform.links.OpenDeeplinkResult.Failed
import build.wallet.platform.links.OpenDeeplinkResult.Opened
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

@BitkeyInject(AppScope::class)
class DeepLinkHandlerImpl : DeepLinkHandler {
  override fun openDeeplink(
    url: String,
    appRestrictions: AppRestrictions?,
  ): OpenDeeplinkResult {
    val nsUrl =
      NSURL.URLWithString(url) ?: run {
        logWarn { "Tried to open an invalid url: $url" }
        return Failed
      }
    UIApplication.sharedApplication.openURL(
      url = nsUrl,
      options = emptyMap<Any?, Any?>(),
      completionHandler = null
    )
    // We do not check app restrictions on IOS,
    // since there is no way to determine the minimum version
    // installed in the app
    return Opened(appRestrictionResult = None)
  }
}
