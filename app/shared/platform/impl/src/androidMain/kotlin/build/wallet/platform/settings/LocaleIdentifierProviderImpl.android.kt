package build.wallet.platform.settings

import build.wallet.platform.PlatformContext

actual class LocaleProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleProvider {
  private val javaLocale by lazy {
    platformContext.appContext.resources.configuration.locales.get(0)
  }

  actual override fun currentLocale(): Locale {
    return Locale(javaLocale.toLanguageTag())
  }
}
