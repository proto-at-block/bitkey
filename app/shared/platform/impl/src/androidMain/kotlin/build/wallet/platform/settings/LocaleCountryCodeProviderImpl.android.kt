package build.wallet.platform.settings

import build.wallet.platform.PlatformContext

actual class LocaleCountryCodeProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleCountryCodeProvider {
  private val countryCode by lazy {
    platformContext.appContext.resources.configuration.locales.get(0).country
  }

  actual override fun countryCode(): String {
    return countryCode
  }
}
