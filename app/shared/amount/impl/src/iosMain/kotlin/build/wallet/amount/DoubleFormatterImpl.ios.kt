package build.wallet.amount

import build.wallet.platform.settings.LocaleIdentifierProvider
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.autoupdatingCurrentLocale

actual class DoubleFormatterImpl(
  /**
   * Provides a way to inject the locale for tests. In actual use, returns the locale directly
   * from NSLocale. We need to access the locale directly from NSLocale instead of initializing
   * via identifier because accessing directly ensures all individual customer settings, like
   * 'Number Format' are included in the locale.
   */
  private val localeProvider: () -> NSLocale,
) : DoubleFormatter {
  @Suppress("UnusedPrivateProperty")
  actual constructor(localeIdentifierProvider: LocaleIdentifierProvider) : this(
    // See note above - we explicitly ignore [LocaleIdentifierProvider] and use NSLocale directly
    localeProvider = { NSLocale.autoupdatingCurrentLocale() }
  )

  actual override fun format(
    double: Double,
    minimumFractionDigits: Int,
    maximumFractionDigits: Int,
    isGroupingUsed: Boolean,
  ): String {
    val formatter =
      formatter.also {
        it.minimumFractionDigits = minimumFractionDigits.toULong()
        it.maximumFractionDigits = maximumFractionDigits.toULong()
        it.usesGroupingSeparator = isGroupingUsed
      }
    return formatter.stringFromNumber(NSNumber(double))!!
  }

  actual override fun parse(string: String): Double? {
    return formatter.numberFromString(string)?.doubleValue
  }

  private val formatter: NSNumberFormatter
    get() =
      NSNumberFormatter().also {
        it.locale = localeProvider()
        it.numberStyle = NSNumberFormatterDecimalStyle
        it.groupingSize = 3u
      }
}
