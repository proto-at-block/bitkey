package build.wallet.platform.settings

import build.wallet.platform.PlatformContext

actual class LocaleProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleProvider {
  actual override fun currentLocale(): Locale = Locale("en-US")
}
