package build.wallet.platform.links

import build.wallet.platform.PlatformContext

actual class DeepLinkHandlerImpl actual constructor(platformContext: PlatformContext) :
  DeepLinkHandler {
    override fun openDeeplink(
      url: String,
      appRestrictions: AppRestrictions?,
    ): OpenDeeplinkResult {
      TODO("Implement for JVM if needed")
    }
  }
