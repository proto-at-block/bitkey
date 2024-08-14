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
  private val shortDateWithTime = NSDateFormatter().apply {
    AMSymbol = "am"
    PMSymbol = "pm"
    dateFormat = "MMM d 'at' h:mm a"
  }

  private val fullShortDateWithTime = NSDateFormatter().apply {
    AMSymbol = "am"
    PMSymbol = "pm"
    dateFormat = "MM/dd/yy 'at' h:mma"
  }

  private val localTimestamp = NSDateFormatter().apply {
    dateFormat = "HH:mm:ss.SSS"
  }

  private val localTime = NSDateFormatter().apply {
    AMSymbol = "am"
    PMSymbol = "pm"
    dateFormat = "h:mma"
  }

  private val shortDate = NSDateFormatter().apply {
    dateFormat = "MMM d"
  }

  private val shortDateWithYear = NSDateFormatter().apply {
    dateFormat = "MMM d, yyyy"
  }

  private val longLocalDate = NSDateFormatter().apply {
    dateStyle = NSDateFormatterLongStyle
    timeStyle = NSDateFormatterNoStyle
  }

  override fun shortDateWithTime(localDateTime: LocalDateTime): String {
    return shortDateWithTime.stringFromDate(localDateTime.toNSDate())
  }

  override fun fullShortDateWithTime(localDateTime: LocalDateTime): String {
    return fullShortDateWithTime.stringFromDate(localDateTime.toNSDate())
  }

  override fun localTimestamp(localDateTime: LocalDateTime): String {
    return localTimestamp.stringFromDate(localDateTime.toNSDate())
  }

  override fun localTime(localDateTime: LocalDateTime): String {
    return localTime.stringFromDate(localDateTime.toNSDate())
  }

  override fun shortDate(localDateTime: LocalDateTime): String {
    return shortDate.stringFromDate(localDateTime.toNSDate())
  }

  override fun shortDateWithYear(localDateTime: LocalDateTime): String {
    return shortDateWithYear.stringFromDate(localDateTime.toNSDate())
  }

  override fun longLocalDate(localDate: LocalDate): String {
    return longLocalDate.stringFromDate(localDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toNSDate())
  }
}
