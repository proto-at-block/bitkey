package build.wallet.platform.settings

interface LocaleLanguageCodeProvider {
  /**
   * Provides the two-letter ISO 639-1 language code from the device's current locale settings,
   * Examples of language codes include "en", "fr", and "de".
   */
  fun languageCode(): String
}
