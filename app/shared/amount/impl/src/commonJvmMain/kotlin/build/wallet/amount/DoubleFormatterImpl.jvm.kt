package build.wallet.amount

import build.wallet.platform.settings.LocaleIdentifierProvider
import java.text.DecimalFormat
import java.util.Locale

actual class DoubleFormatterImpl actual constructor(
  private val localeIdentifierProvider: LocaleIdentifierProvider,
) : DoubleFormatter {
  actual override fun format(
    double: Double,
    minimumFractionDigits: Int,
    maximumFractionDigits: Int,
    isGroupingUsed: Boolean,
  ): String {
    val formatter =
      DecimalFormat.getInstance(locale).also {
        it.minimumFractionDigits = minimumFractionDigits
        it.maximumFractionDigits = maximumFractionDigits
        it.isGroupingUsed = isGroupingUsed
      }
    return formatter.format(double)
  }

  actual override fun parse(string: String): Double? {
    return DecimalFormat.getInstance(locale)
      .parse(string)?.toDouble()
  }

  private val locale: Locale
    get() = Locale.forLanguageTag(localeIdentifierProvider.localeIdentifier())
}
