package build.wallet.amount

import build.wallet.platform.settings.LocaleProvider
import build.wallet.platform.settings.toNSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle

actual class DoubleFormatterImpl actual constructor(
  private val localeProvider: LocaleProvider,
) : DoubleFormatter {
  actual override fun format(
    double: Double,
    minimumFractionDigits: Int,
    maximumFractionDigits: Int,
    isGroupingUsed: Boolean,
  ): String {
    val formatter = numberFormatter().also {
      it.minimumFractionDigits = minimumFractionDigits.toULong()
      it.maximumFractionDigits = maximumFractionDigits.toULong()
      it.usesGroupingSeparator = isGroupingUsed
    }
    return formatter.stringFromNumber(NSNumber(double))!!
  }

  actual override fun parse(string: String): Double? {
    return numberFormatter().numberFromString(string)?.doubleValue
  }

  private fun numberFormatter() =
    NSNumberFormatter().also {
      it.locale = localeProvider.currentLocale().toNSLocale()
      it.numberStyle = NSNumberFormatterDecimalStyle
      it.groupingSize = 3u
    }
}
