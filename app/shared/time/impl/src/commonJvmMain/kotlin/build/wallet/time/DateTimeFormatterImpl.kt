package build.wallet.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import java.time.format.DateTimeFormatter as JavaDateTimeFormatter

actual class DateTimeFormatterImpl : DateTimeFormatter {
  override fun shortDateWithTime(localDateTime: LocalDateTime): String {
    val javaLocalDateTime = localDateTime.toJavaLocalDateTime()

    val datePattern = "MMM d"
    val date = JavaDateTimeFormatter.ofPattern(datePattern).format(javaLocalDateTime)

    val timePattern = "h:mm a"
    val time =
      JavaDateTimeFormatter.ofPattern(timePattern)
        .format(javaLocalDateTime)
        .lowercase()

    return "$date at $time"
  }

  override fun fullShortDateWithTime(localDateTime: LocalDateTime): String {
    val javaLocalDateTime = localDateTime.toJavaLocalDateTime()

    val datePattern = "MM/dd/yy"
    val date = JavaDateTimeFormatter.ofPattern(datePattern).format(javaLocalDateTime)

    val timePattern = "h:mma"
    val time =
      JavaDateTimeFormatter.ofPattern(timePattern)
        .format(javaLocalDateTime)
        .lowercase()

    return "$date at $time"
  }

  override fun localTimestamp(localDateTime: LocalDateTime): String {
    val javaLocalDateTime = localDateTime.toJavaLocalDateTime()

    return JavaDateTimeFormatter.ISO_LOCAL_TIME.format(javaLocalDateTime)
  }

  override fun localTime(localDateTime: LocalDateTime): String {
    val javaLocalDateTime = localDateTime.toJavaLocalDateTime()
    val timePattern = "h:mma"

    return JavaDateTimeFormatter.ofPattern(timePattern)
      .format(javaLocalDateTime)
      .lowercase()
  }

  override fun shortDate(localDateTime: LocalDateTime): String {
    val javaLocalDateTime = localDateTime.toJavaLocalDateTime()
    val datePattern = "MMM d"

    return JavaDateTimeFormatter.ofPattern(datePattern)
      .format(javaLocalDateTime)
  }

  override fun shortDateWithYear(localDateTime: LocalDateTime): String {
    val javaLocalDateTime = localDateTime.toJavaLocalDateTime()
    val datePattern = "MMM d, YYYY"

    return JavaDateTimeFormatter.ofPattern(datePattern)
      .format(javaLocalDateTime)
  }

  override fun longLocalDate(localDate: LocalDate): String {
    val formatter = JavaDateTimeFormatter.ofPattern("MMMM d, YYYY")
    return localDate.toJavaLocalDate().format(formatter)
  }
}
