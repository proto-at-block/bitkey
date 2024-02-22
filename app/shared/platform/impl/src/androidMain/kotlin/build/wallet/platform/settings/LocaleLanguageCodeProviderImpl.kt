package build.wallet.platform.settings

import build.wallet.platform.PlatformContext

actual class LocaleLanguageCodeProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleLanguageCodeProvider {
  private val languageCode by lazy {
    platformContext.appContext.resources.configuration.locales.get(0).language
  }

  override fun languageCode(): String {
    return languageCode
  }
}
