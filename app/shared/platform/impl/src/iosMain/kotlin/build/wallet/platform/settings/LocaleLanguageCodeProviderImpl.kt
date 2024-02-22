package build.wallet.platform.settings

import build.wallet.platform.PlatformContext
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

actual class LocaleLanguageCodeProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleLanguageCodeProvider {
  override fun languageCode(): String {
    return NSLocale.currentLocale().languageCode
  }
}
