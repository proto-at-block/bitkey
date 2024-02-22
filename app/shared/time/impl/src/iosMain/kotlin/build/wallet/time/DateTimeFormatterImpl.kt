package build.wallet.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toNSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterNoStyle

actual class DateTimeFormatterImpl : DateTimeFormatter {
  override fun shortDateWithTime(localDateTime: LocalDateTime): String {
    return NSDateFormatter().apply {
      AMSymbol = "am"
      PMSymbol = "pm"
      dateFormat = "MMM d 'at' h:mm a"
    }.stringFromDate(localDateTime.toNSDate())
  }

  override fun fullShortDateWithTime(localDateTime: LocalDateTime): String {
    return NSDateFormatter().apply {
      AMSymbol = "am"
      PMSymbol = "pm"
      dateFormat = "MM/dd/yy 'at' h:mma"
    }.stringFromDate(localDateTime.toNSDate())
  }

  override fun localTimestamp(localDateTime: LocalDateTime): String {
    return NSDateFormatter().apply {
      dateFormat = "HH:mm:ss.SSS"
    }.stringFromDate(localDateTime.toNSDate())
  }

  override fun localTime(localDateTime: LocalDateTime): String {
    return NSDateFormatter().apply {
      AMSymbol = "am"
      PMSymbol = "pm"
      dateFormat = "h:mma"
    }.stringFromDate(localDateTime.toNSDate())
  }

  override fun shortDate(localDateTime: LocalDateTime): String {
    return NSDateFormatter().apply {
      dateFormat = "MMM d"
    }.stringFromDate(localDateTime.toNSDate())
  }

  override fun shortDateWithYear(localDateTime: LocalDateTime): String {
    return NSDateFormatter().apply {
      dateFormat = "MMM d, yyyy"
    }.stringFromDate(localDateTime.toNSDate())
  }

  override fun longLocalDate(localDate: LocalDate): String {
    return NSDateFormatter().apply {
      dateStyle = NSDateFormatterLongStyle
      timeStyle = NSDateFormatterNoStyle
    }.stringFromDate(localDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toNSDate())
  }
}
