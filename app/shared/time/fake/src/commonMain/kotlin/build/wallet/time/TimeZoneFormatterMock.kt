package build.wallet.time

import kotlinx.datetime.TimeZone

class TimeZoneFormatterMock : TimeZoneFormatter {
  override fun timeZoneShortName(
    timeZone: TimeZone,
    localeIdentifier: String,
  ): String = "PDT"
}
