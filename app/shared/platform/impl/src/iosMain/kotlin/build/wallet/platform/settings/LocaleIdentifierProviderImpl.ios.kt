package build.wallet.platform.settings

import build.wallet.platform.PlatformContext
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

actual class LocaleIdentifierProviderImpl actual constructor(
  platformContext: PlatformContext,
) : LocaleIdentifierProvider {
  actual override fun localeIdentifier(): String {
    return NSLocale.currentLocale().localeIdentifier
  }
}
