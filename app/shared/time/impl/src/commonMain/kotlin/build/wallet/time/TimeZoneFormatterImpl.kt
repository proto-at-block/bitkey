package build.wallet.time

import kotlinx.datetime.TimeZone

expect class TimeZoneFormatterImpl constructor() : TimeZoneFormatter {
  override fun timeZoneShortName(
    timeZone: TimeZone,
    localeIdentifier: String,
  ): String
}
