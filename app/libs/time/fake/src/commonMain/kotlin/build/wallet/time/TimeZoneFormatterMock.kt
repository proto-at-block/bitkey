package build.wallet.time

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

class TimeZoneFormatterMock : TimeZoneFormatter {
  override fun timeZoneShortName(
    timeZone: TimeZone,
    clock: Clock,
  ): String = "PDT"
}
