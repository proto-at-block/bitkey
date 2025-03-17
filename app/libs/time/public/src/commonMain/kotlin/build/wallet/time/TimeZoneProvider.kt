package build.wallet.time

import kotlinx.datetime.TimeZone

interface TimeZoneProvider {
  fun current(): TimeZone
}
