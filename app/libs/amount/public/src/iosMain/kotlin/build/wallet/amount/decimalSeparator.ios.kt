package build.wallet.amount

import build.wallet.platform.settings.Locale
import build.wallet.platform.settings.toNSLocale
import platform.Foundation.decimalSeparator

actual val Locale.decimalSeparator: Char
  get() = toNSLocale().decimalSeparator.first()
