package build.wallet.amount

import build.wallet.platform.settings.LocaleProvider

expect class DoubleFormatterImpl(
  localeProvider: LocaleProvider,
) : DoubleFormatter {
  override fun format(
    double: Double,
    minimumFractionDigits: Int,
    maximumFractionDigits: Int,
    isGroupingUsed: Boolean,
  ): String

  override fun parse(string: String): Double?
}
