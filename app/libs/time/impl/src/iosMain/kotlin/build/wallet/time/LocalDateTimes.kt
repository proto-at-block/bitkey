package build.wallet.time

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toNSDateComponents
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate

internal fun LocalDateTime.toNSDate(): NSDate =
  NSCalendar.currentCalendar.dateFromComponents(toNSDateComponents())!!
