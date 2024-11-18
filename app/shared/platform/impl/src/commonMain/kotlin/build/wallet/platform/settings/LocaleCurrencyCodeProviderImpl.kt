package build.wallet.platform.settings

import build.wallet.platform.PlatformContext

expect class LocaleCurrencyCodeProviderImpl constructor(
  platformContext: PlatformContext,
) : LocaleCurrencyCodeProvider {
  override fun localeCurrencyCode(): String?
}
