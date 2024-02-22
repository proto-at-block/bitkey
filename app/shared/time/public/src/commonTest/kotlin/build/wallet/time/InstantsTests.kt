package build.wallet.time

import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class InstantsTests : FunSpec({
  test("result ok") {
    "2023-01-10T08:22:25.736822Z".toInstantResult()
      .shouldBe(
        Ok(
          LocalDateTime(
            year = 2023,
            monthNumber = 1,
            dayOfMonth = 10,
            hour = 8,
            minute = 22,
            second = 25,
            nanosecond = 736_822_000
          ).toInstant(offset = UtcOffset.ZERO)
        )
      )
  }

  test("result err") {
    "2023-01-10T08:22:25".toInstantResult()
      .shouldBeErrOfType<InstantParsingError>()
  }

  test("is today") {
    val instant = "2023-01-10T08:22:25.736822Z".toInstantResult().getOrThrow()
    val instantMinus4Hours = instant.minus(4.hours)
    instantMinus4Hours.isToday(
      clock = ClockFake(now = instant),
      timeZoneProvider = TimeZoneProviderMock()
    ).shouldBeTrue()
  }

  test("is not today") {
    val instant = "2023-01-10T08:22:25.736822Z".toInstantResult().getOrThrow()
    val instantMinus2days = instant.minus(2.days)

    instantMinus2days.isToday(
      clock = ClockFake(now = instant),
      timeZoneProvider = TimeZoneProviderMock()
    ).shouldBeFalse()
  }

  test("is this year") {
    val instant = "2023-01-10T08:22:25.736822Z".toInstantResult().getOrThrow()
    val instantMinus5days = instant.minus(5.days)
    instantMinus5days.isThisYear(
      clock = ClockFake(now = instant),
      timeZoneProvider = TimeZoneProviderMock()
    ).shouldBeTrue()
  }

  test("is not this year") {
    val instant = "2023-01-10T08:22:25.736822Z".toInstantResult().getOrThrow()
    val instantMinus6months = instant.minus(30.days * 6)

    instantMinus6months.isThisYear(
      clock = ClockFake(now = instant),
      timeZoneProvider = TimeZoneProviderMock()
    ).shouldBeFalse()
  }
})
