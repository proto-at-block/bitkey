package build.wallet.platform.settings

import build.wallet.platform.PlatformContext

expect class LocaleLanguageCodeProviderImpl constructor(
  platformContext: PlatformContext,
) : LocaleLanguageCodeProvider
