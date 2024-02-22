package build.wallet.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toNSDate
import platform.Foundation.NSDate

fun LocalDate.toNativeDate(): NSDate {
  return atStartOfDayIn(TimeZone.currentSystemDefault()).toNSDate()
}

fun NSDate.toLocalDate(): LocalDate {
  return toKotlinInstant().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
