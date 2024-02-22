package build.wallet.platform.settings

/**
 * Locales supported by [LocaleIdentifierProviderFake].
 */
enum class TestLocale {
  EN_US,
  FR_FR,
  ;

  fun androidLocaleIdentifier(): String =
    when (this) {
      EN_US -> "en-US"
      FR_FR -> "fr-FR"
    }

  fun iosLocaleIdentifier(): String =
    when (this) {
      EN_US -> "en_US"
      FR_FR -> "fr_FR"
    }
}
