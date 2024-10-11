package build.wallet.amount

import build.wallet.platform.settings.LocaleIdentifierProvider
import platform.Foundation.NSLocale
import platform.Foundation.autoupdatingCurrentLocale
import platform.Foundation.decimalSeparator

actual class DecimalSeparatorProviderImpl(
  /**
   * Provides a way to inject the locale for tests. In actual use, returns the locale directly
   * from NSLocale. We need to access the locale directly from NSLocale instead of initializing
   * via identifier because accessing directly ensures all individual customer settings, like
   * 'Number Format' are included in the locale.
   */
  private val localeProvider: () -> NSLocale,
) : DecimalSeparatorProvider {
  @Suppress("UnusedPrivateProperty")
  actual constructor(localeIdentifierProvider: LocaleIdentifierProvider) : this(
    // See note above - we explicitly ignore [LocaleIdentifierProvider] and use NSLocale directly
    localeProvider = { NSLocale.autoupdatingCurrentLocale() }
  )

  actual override fun decimalSeparator(): Char = localeProvider().decimalSeparator.first()
}
