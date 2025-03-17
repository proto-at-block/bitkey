package build.wallet.amount

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.settings.LocaleProvider
import build.wallet.platform.settings.toJavaLocale
import java.text.DecimalFormat

@BitkeyInject(AppScope::class)
actual class DoubleFormatterImpl actual constructor(
  private val localeProvider: LocaleProvider,
) : DoubleFormatter {
  actual override fun format(
    double: Double,
    minimumFractionDigits: Int,
    maximumFractionDigits: Int,
    isGroupingUsed: Boolean,
  ): String {
    val formatter = decimalFormat().also {
      it.minimumFractionDigits = minimumFractionDigits
      it.maximumFractionDigits = maximumFractionDigits
      it.isGroupingUsed = isGroupingUsed
    }
    return formatter.format(double)
  }

  actual override fun parse(string: String): Double? {
    return decimalFormat()
      .parse(string)?.toDouble()
  }

  private fun decimalFormat() =
    DecimalFormat.getInstance(localeProvider.currentLocale().toJavaLocale())
}
