package build.wallet.platform.settings

import build.wallet.platform.PlatformContext

expect class LocaleCountryCodeProviderImpl constructor(
  platformContext: PlatformContext,
) : LocaleCountryCodeProvider
