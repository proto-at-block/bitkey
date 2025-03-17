package build.wallet.time

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlin.time.Duration.Companion.milliseconds

class DateTimeFormatterImplTests : FunSpec({
  val formatter = DateTimeFormatterImpl()
  val localDate = LocalDate(year = 2021, month = Month.NOVEMBER, dayOfMonth = 14)
  val localTime =
    LocalTime(
      hour = 5,
      minute = 15,
      second = 6,
      nanosecond = 123.milliseconds.inWholeNanoseconds.toInt()
    )
  val localDateTime = LocalDateTime(localDate, localTime)

  test("format short date with time") {
    formatter.shortDateWithTime(localDateTime).shouldBe("Nov 14 at 5:15 am")
  }

  test("format full short date with time") {
    formatter.fullShortDateWithTime(localDateTime).shouldBe("11/14/21 at 5:15am")
  }

  test("format timestamp") {
    formatter.localTimestamp(localDateTime).shouldBe("05:15:06.123")
  }

  test("format local time") {
    formatter.localTime(localDateTime).shouldBe("5:15am")
  }

  test("format short date") {
    formatter.shortDate(localDateTime).shouldBe("Nov 14")
  }

  test("format short date with year") {
    formatter.shortDateWithYear(localDateTime).shouldBe("Nov 14, 2021")
  }
})
