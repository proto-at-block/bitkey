package build.wallet.platform.settings

interface LocaleCurrencyCodeProvider {
  /**
   * Provides the text code identifying the currency corresponding to
   * the current device's locale settings, e.g. USD.
   */
  fun localeCurrencyCode(): String?
}
