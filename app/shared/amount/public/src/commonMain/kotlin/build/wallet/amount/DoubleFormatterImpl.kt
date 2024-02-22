package build.wallet.amount

import build.wallet.platform.settings.LocaleIdentifierProvider

expect class DoubleFormatterImpl(
  localeIdentifierProvider: LocaleIdentifierProvider,
) : DoubleFormatter
