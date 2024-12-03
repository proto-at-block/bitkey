package build.wallet.platform.settings

import build.wallet.platform.PlatformContext
import platform.Foundation.NSLocale
import platform.Foundation.autoupdatingCurrentLocale
import platform.Foundation.localeIdentifier

actual class LocaleProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleProvider {
  actual override fun currentLocale(): Locale {
    return Locale(value = NSLocale.autoupdatingCurrentLocale().localeIdentifier)
  }
}
