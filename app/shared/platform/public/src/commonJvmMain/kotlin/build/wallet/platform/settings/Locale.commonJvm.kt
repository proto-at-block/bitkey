package build.wallet.platform.settings

fun Locale.toJavaLocale(): java.util.Locale = java.util.Locale.forLanguageTag(value)
