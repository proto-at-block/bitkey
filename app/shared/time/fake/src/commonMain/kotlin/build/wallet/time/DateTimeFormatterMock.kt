package build.wallet.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime

class DateTimeFormatterMock(
  private val timeToFormattedTime: Map<LocalDateTime, String>? = null,
) : DateTimeFormatter {
  override fun shortDateWithTime(localDateTime: LocalDateTime): String {
    return timeToFormattedTime?.let { it[localDateTime] } ?: "date-time"
  }

  override fun fullShortDateWithTime(localDateTime: LocalDateTime): String {
    return timeToFormattedTime?.let { it[localDateTime] } ?: "date-time"
  }

  override fun localTimestamp(localDateTime: LocalDateTime): String {
    return "timestamp"
  }

  override fun localTime(localDateTime: LocalDateTime): String {
    return timeToFormattedTime?.let { it[localDateTime] } ?: "date-time"
  }

  override fun shortDate(localDateTime: LocalDateTime): String {
    return timeToFormattedTime?.let { it[localDateTime] } ?: "date-time"
  }

  override fun shortDateWithYear(localDateTime: LocalDateTime): String {
    return timeToFormattedTime?.let { it[localDateTime] } ?: "date-time"
  }

  override fun longLocalDate(localDate: LocalDate): String {
    return timeToFormattedTime?.let { it[localDate.atTime(hour = 0, minute = 0)] } ?: "date"
  }
}
