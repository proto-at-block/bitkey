package build.wallet.time

import build.wallet.platform.settings.LocaleProvider
import build.wallet.platform.settings.toNSLocale
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toNSTimeZone
import platform.Foundation.NSTimeZoneNameStyle.NSTimeZoneNameStyleShortDaylightSaving
import platform.Foundation.localizedName

actual class TimeZoneFormatterImpl actual constructor(
  private val localeProvider: LocaleProvider,
) : TimeZoneFormatter {
  actual override fun timeZoneShortName(timeZone: TimeZone): String {
    val nsTimeZone = timeZone.toNSTimeZone()
    val locale = localeProvider.currentLocale()
    return checkNotNull(
      nsTimeZone
        .localizedName(
          style = NSTimeZoneNameStyleShortDaylightSaving,
          locale = locale.toNSLocale()
        )
    ) {
      "Could not get short name for timezone $nsTimeZone and locale $locale"
    }
  }
}
