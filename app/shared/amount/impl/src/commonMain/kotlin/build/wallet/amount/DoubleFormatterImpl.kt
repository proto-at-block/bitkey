package build.wallet.amount

import build.wallet.platform.settings.LocaleIdentifierProvider

expect class DoubleFormatterImpl(
  localeIdentifierProvider: LocaleIdentifierProvider,
) : DoubleFormatter {
  override fun format(
    double: Double,
    minimumFractionDigits: Int,
    maximumFractionDigits: Int,
    isGroupingUsed: Boolean,
  ): String

  override fun parse(string: String): Double?
}
