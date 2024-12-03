package build.wallet.amount

import build.wallet.platform.settings.Locale

/**
 * Returns the decimal separator based on given [Locale].
 *
 * A decimal separator is a symbol used to separate the integer part from the
 * fractional part of a number written in decimal form (e.g. "." in 12.45)
 * Example decimal separators include "." and "," - depend on [Locale].
 */
expect val Locale.decimalSeparator: Char
