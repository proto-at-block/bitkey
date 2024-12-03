package build.wallet.time

import build.wallet.platform.settings.LocaleProvider
import kotlinx.datetime.TimeZone

expect class TimeZoneFormatterImpl(
  localeProvider: LocaleProvider,
) : TimeZoneFormatter {
  override fun timeZoneShortName(timeZone: TimeZone): String
}
