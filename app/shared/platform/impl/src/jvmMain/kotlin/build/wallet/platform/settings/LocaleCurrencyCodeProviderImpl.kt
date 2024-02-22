package build.wallet.platform.settings

import build.wallet.platform.PlatformContext

actual class LocaleCurrencyCodeProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleCurrencyCodeProvider {
  override fun localeCurrencyCode(): String? {
    return "USD"
  }
}
