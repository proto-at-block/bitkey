package build.wallet.platform.settings

import build.wallet.platform.PlatformContext

actual class TelephonyCountryCodeProviderImpl actual constructor(
  platformContext: PlatformContext,
) : TelephonyCountryCodeProvider {
  override fun countryCode(): String = "US"
}
