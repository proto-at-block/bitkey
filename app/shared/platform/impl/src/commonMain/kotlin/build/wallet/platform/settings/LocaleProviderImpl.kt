package build.wallet.platform.settings

import build.wallet.platform.PlatformContext

expect class LocaleProviderImpl(
  platformContext: PlatformContext,
) : LocaleProvider {
  override fun currentLocale(): Locale
}
