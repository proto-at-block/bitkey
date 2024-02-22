package build.wallet.time

import kotlinx.datetime.TimeZone

class TimeZoneProviderImpl : TimeZoneProvider {
  override fun current(): TimeZone = TimeZone.currentSystemDefault()
}
