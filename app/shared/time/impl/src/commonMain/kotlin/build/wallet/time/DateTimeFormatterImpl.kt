package build.wallet.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

expect class DateTimeFormatterImpl constructor() : DateTimeFormatter {
  override fun shortDateWithTime(localDateTime: LocalDateTime): String

  override fun fullShortDateWithTime(localDateTime: LocalDateTime): String

  override fun localTimestamp(localDateTime: LocalDateTime): String

  override fun localTime(localDateTime: LocalDateTime): String

  override fun shortDate(localDateTime: LocalDateTime): String

  override fun shortDateWithYear(localDateTime: LocalDateTime): String

  override fun longLocalDate(localDate: LocalDate): String
}
