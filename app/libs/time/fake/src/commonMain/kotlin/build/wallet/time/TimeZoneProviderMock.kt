package build.wallet.time

import kotlinx.datetime.TimeZone

class TimeZoneProviderMock(
  var current: TimeZone = TimeZone.UTC,
) : TimeZoneProvider {
  override fun current(): TimeZone = current
}
