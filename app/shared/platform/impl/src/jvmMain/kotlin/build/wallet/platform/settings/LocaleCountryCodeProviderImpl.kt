package build.wallet.platform.settings

import build.wallet.platform.PlatformContext

actual class LocaleCountryCodeProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleCountryCodeProvider {
  override fun countryCode(): String = "US"
}
