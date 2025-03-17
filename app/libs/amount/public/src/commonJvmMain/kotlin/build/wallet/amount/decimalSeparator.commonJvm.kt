package build.wallet.amount

import build.wallet.platform.settings.Locale
import build.wallet.platform.settings.toJavaLocale
import java.text.DecimalFormatSymbols

actual val Locale.decimalSeparator: Char
  get() = DecimalFormatSymbols
    .getInstance(toJavaLocale())
    .decimalSeparator
