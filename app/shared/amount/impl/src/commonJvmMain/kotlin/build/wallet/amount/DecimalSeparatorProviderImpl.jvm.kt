package build.wallet.amount

import build.wallet.platform.settings.LocaleIdentifierProvider
import java.text.DecimalFormatSymbols
import java.util.Locale

actual class DecimalSeparatorProviderImpl actual constructor(
  private val localeIdentifierProvider: LocaleIdentifierProvider,
) : DecimalSeparatorProvider {
  actual override fun decimalSeparator(): Char =
    DecimalFormatSymbols
      .getInstance(Locale.forLanguageTag(localeIdentifierProvider.localeIdentifier()))
      .decimalSeparator
}
