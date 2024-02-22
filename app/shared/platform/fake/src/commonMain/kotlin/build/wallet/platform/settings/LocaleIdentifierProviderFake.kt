package build.wallet.platform.settings

import build.wallet.platform.settings.TestLocale.EN_US

/**
 * Fake [LocaleIdentifierProvider] that manually maps locales to identifier string representation.
 */
class LocaleIdentifierProviderFake(
  var locale: TestLocale = EN_US,
) : LocaleIdentifierProvider {
  override fun localeIdentifier(): String {
    // This is only used on Android. iOS uses NSLocale directly.
    return locale.androidLocaleIdentifier()
  }
}
