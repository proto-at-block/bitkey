package build.wallet.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

interface DateTimeFormatter {
  /**
   * Formats instants with a given timezone in a format like "Nov 14 at 5:15 am".
   */
  fun shortDateWithTime(localDateTime: LocalDateTime): String

  /**
   * Formats instants with a given timezone in a format like "11/14/23 at 5:15am".
   */
  fun fullShortDateWithTime(localDateTime: LocalDateTime): String

  /**
   * Format [LocalDateTime] as a local timestamp, for example `05:15:06.123`, where `05` is hours,
   * `15` is minutes, `06` is seconds and `123` is milliseconds. Mostly used for debugging purposes.
   */
  fun localTimestamp(localDateTime: LocalDateTime): String

  /**
   * Format [LocalDateTime] as a local time, for example `5:15am` or `5:15pm`.
   */
  fun localTime(localDateTime: LocalDateTime): String

  /**
   * Formats [LocalDateTime] as a short date, for example `Nov 14`.
   */
  fun shortDate(localDateTime: LocalDateTime): String

  /**
   * Formats [LocalDateTime] as a short date with year, for example `Nov 14, 2021`.
   */
  fun shortDateWithYear(localDateTime: LocalDateTime): String

  /**
   * Format [LocalDate] as long representation of the date.
   * Example: `February 5, 2024`
   */
  fun longLocalDate(localDate: LocalDate): String
}
