package build.wallet.platform.links

import build.wallet.platform.PlatformContext

expect class DeepLinkHandlerImpl constructor(
  platformContext: PlatformContext,
) : DeepLinkHandler
