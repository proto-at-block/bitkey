package build.wallet.amount

import build.wallet.platform.settings.LocaleIdentifierProvider

expect class DecimalSeparatorProviderImpl(
  localeIdentifierProvider: LocaleIdentifierProvider,
) : DecimalSeparatorProvider {
  override fun decimalSeparator(): Char
}
