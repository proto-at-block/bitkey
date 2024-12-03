package build.wallet.time

import build.wallet.platform.settings.LocaleProvider
import build.wallet.platform.settings.toJavaLocale
import kotlinx.datetime.TimeZone
import java.util.TimeZone as JavaTimeZone

actual class TimeZoneFormatterImpl actual constructor(
  private val localeProvider: LocaleProvider,
) : TimeZoneFormatter {
  actual override fun timeZoneShortName(timeZone: TimeZone): String {
    return JavaTimeZone
      .getTimeZone(timeZone.id)
      .getDisplayName(
        true, // daylight time
        0, // style = SHORT
        localeProvider.currentLocale().toJavaLocale()
      )
  }
}
