package build.wallet.platform.settings

import build.wallet.platform.PlatformContext

expect class TelephonyCountryCodeProviderImpl constructor(
  platformContext: PlatformContext,
) : TelephonyCountryCodeProvider
