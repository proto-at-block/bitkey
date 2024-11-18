package build.wallet.platform.settings

import build.wallet.platform.PlatformContext

actual class LocaleCountryCodeProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleCountryCodeProvider {
  actual override fun countryCode(): String = "US"
}
