package build.wallet.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import java.time.temporal.ChronoField
import java.util.Locale
import java.time.format.DateTimeFormatter as JavaDateTimeFormatter
import java.time.format.DateTimeFormatterBuilder as JavaDateTimeFormatterBuilder

actual class DateTimeFormatterImpl : DateTimeFormatter {
  private val amPmMap = mapOf(0L to "am", 1L to "pm")

  private val shortDateWithTime = JavaDateTimeFormatterBuilder()
    .appendPattern("MMM d 'at' h:mm ")
    .appendText(ChronoField.AMPM_OF_DAY, amPmMap)
    .toFormatter(Locale.ENGLISH)

  private val fullShortDateWithTime = JavaDateTimeFormatterBuilder()
    .appendPattern("MM/dd/yy 'at' h:mm")
    .appendText(ChronoField.AMPM_OF_DAY, amPmMap)
    .toFormatter(Locale.ENGLISH)

  private val localTime = JavaDateTimeFormatter
    .ofPattern("h:mma", Locale.ENGLISH)

  private val shortDate = JavaDateTimeFormatter
    .ofPattern("MMM d", Locale.ENGLISH)

  private val shortDateWithYear = JavaDateTimeFormatter
    .ofPattern("MMM d, YYYY", Locale.ENGLISH)

  private val longLocalDate = JavaDateTimeFormatter
    .ofPattern("MMMM d, YYYY", Locale.ENGLISH)

  actual override fun shortDateWithTime(localDateTime: LocalDateTime): String {
    val javaLocalDateTime = localDateTime.toJavaLocalDateTime()
    return shortDateWithTime.format(javaLocalDateTime)
  }

  actual override fun fullShortDateWithTime(localDateTime: LocalDateTime): String {
    val javaLocalDateTime = localDateTime.toJavaLocalDateTime()
    return fullShortDateWithTime.format(javaLocalDateTime)
  }

  actual override fun localTimestamp(localDateTime: LocalDateTime): String {
    val javaLocalDateTime = localDateTime.toJavaLocalDateTime()

    return JavaDateTimeFormatter.ISO_LOCAL_TIME.format(javaLocalDateTime)
  }

  actual override fun localTime(localDateTime: LocalDateTime): String {
    val javaLocalDateTime = localDateTime.toJavaLocalDateTime()

    return localTime.format(javaLocalDateTime).lowercase()
  }

  actual override fun shortDate(localDateTime: LocalDateTime): String {
    val javaLocalDateTime = localDateTime.toJavaLocalDateTime()

    return shortDate.format(javaLocalDateTime)
  }

  actual override fun shortDateWithYear(localDateTime: LocalDateTime): String {
    val javaLocalDateTime = localDateTime.toJavaLocalDateTime()

    return shortDateWithYear.format(javaLocalDateTime)
  }

  actual override fun longLocalDate(localDate: LocalDate): String {
    return localDate.toJavaLocalDate().format(longLocalDate)
  }
}
