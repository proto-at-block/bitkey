package build.wallet.time

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toNSTimeZone
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZoneNameStyle.NSTimeZoneNameStyleShortDaylightSaving
import platform.Foundation.localizedName

actual class TimeZoneFormatterImpl : TimeZoneFormatter {
  override fun timeZoneShortName(
    timeZone: TimeZone,
    localeIdentifier: String,
  ): String {
    val nsTimeZone = timeZone.toNSTimeZone()
    return checkNotNull(
      nsTimeZone
        .localizedName(
          style = NSTimeZoneNameStyleShortDaylightSaving,
          locale = NSLocale(localeIdentifier)
        )
    ) {
      "Could not get short name for timezone $nsTimeZone and locale $localeIdentifier"
    }
  }
}
