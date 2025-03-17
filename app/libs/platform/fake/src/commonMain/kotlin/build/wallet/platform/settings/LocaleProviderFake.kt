package build.wallet.platform.settings

/**
 * Fake [LocaleProvider] that manually maps locales to identifier string representation.
 */
class LocaleProviderFake : LocaleProvider {
  var locale = Locale.EN_US

  override fun currentLocale(): Locale = locale

  fun reset() {
    locale = Locale.EN_US
  }
}
