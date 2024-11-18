package build.wallet.platform.settings

import build.wallet.platform.PlatformContext

actual class LocaleLanguageCodeProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleLanguageCodeProvider {
  actual override fun languageCode(): String = "en"
}
