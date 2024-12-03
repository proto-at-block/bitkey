package build.wallet.platform.settings

import platform.Foundation.NSLocale

fun Locale.toNSLocale(): NSLocale = NSLocale(localeIdentifier = value)
