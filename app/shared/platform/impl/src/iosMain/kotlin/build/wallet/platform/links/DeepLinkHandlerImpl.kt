package build.wallet.platform.links

import build.wallet.logging.LogLevel.Warn
import build.wallet.logging.log
import build.wallet.platform.PlatformContext
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult.None
import build.wallet.platform.links.OpenDeeplinkResult.Failed
import build.wallet.platform.links.OpenDeeplinkResult.Opened
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual class DeepLinkHandlerImpl actual constructor(
  platformContext: PlatformContext,
) : DeepLinkHandler {
  override fun openDeeplink(
    url: String,
    appRestrictions: AppRestrictions?,
  ): OpenDeeplinkResult {
    val nsUrl =
      NSURL.URLWithString(url) ?: run {
        log(Warn) { "Tried to open an invalid url: $url" }
        return Failed
      }
    UIApplication.sharedApplication.openURL(nsUrl)
    // We do not check app restrictions on IOS,
    // since there is no way to determine the minimum version
    // installed in the app
    return Opened(appRestrictionResult = None)
  }
}
