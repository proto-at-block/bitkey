package build.wallet.platform.links

import build.wallet.platform.PlatformContext

expect class DeepLinkHandlerImpl constructor(
  platformContext: PlatformContext,
) : DeepLinkHandler {
  override fun openDeeplink(
    url: String,
    appRestrictions: AppRestrictions?,
  ): OpenDeeplinkResult
}
