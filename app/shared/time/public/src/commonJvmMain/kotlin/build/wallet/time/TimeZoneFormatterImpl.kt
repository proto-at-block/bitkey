package build.wallet.time

import kotlinx.datetime.TimeZone
import java.util.Locale
import java.util.TimeZone as JavaTimeZone

actual class TimeZoneFormatterImpl : TimeZoneFormatter {
  override fun timeZoneShortName(
    timeZone: TimeZone,
    localeIdentifier: String,
  ): String {
    return JavaTimeZone
      .getTimeZone(timeZone.id)
      .getDisplayName(
        true, // daylight time
        0, // style = SHORT
        Locale.forLanguageTag(localeIdentifier)
      )
  }
}
