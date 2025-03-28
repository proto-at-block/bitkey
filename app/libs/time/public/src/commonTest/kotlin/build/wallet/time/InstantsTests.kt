package build.wallet.time

import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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

  context("truncateToMilliseconds") {
    val instantWithMilliseconds = "2023-01-10T08:22:25.736Z".toInstant()

    test("truncates instant with microseconds to milliseconds") {
      val instantWithMicroseconds = "2023-01-10T08:22:25.736822Z".toInstant()
      instantWithMicroseconds.truncateToMilliseconds().shouldBe(instantWithMilliseconds)
    }

    test("truncates instant with nanoseconds to milliseconds") {
      val instantWithNanoseconds = "2023-01-10T08:22:25.736999Z".toInstant()
      instantWithNanoseconds.truncateToMilliseconds().shouldBe(instantWithMilliseconds)
    }

    test("does not modify an already truncated instant") {
      val alreadyTruncatedInstant = "2023-01-10T08:22:25.736Z".toInstant()
      alreadyTruncatedInstant.truncateToMilliseconds().shouldBe(instantWithMilliseconds)
    }

    test("truncates instant to nearest lower millisecond") {
      val instantWithUpperMicroseconds = "2023-01-10T08:22:25.736499Z".toInstant()
      instantWithUpperMicroseconds.truncateToMilliseconds().shouldBe(instantWithMilliseconds)
    }

    test("does not modify instant without fractional milliseconds") {
      val instantWithoutFractionalMilliseconds = "2023-01-10T08:22:25Z".toInstant()
      instantWithoutFractionalMilliseconds.truncateToMilliseconds()
        .shouldBe(instantWithoutFractionalMilliseconds)
    }

    test("handles full second rounding") {
      val instantWithFullSecond = "2023-01-10T08:22:26.000Z".toInstant()
      instantWithFullSecond.truncateToMilliseconds().shouldBe(instantWithFullSecond)
    }
  }

  context("truncateTo") {
    val instant = Instant.parse("2025-02-15T12:46:44Z")
    test("10.minutes") {
      instant.truncateTo(10.minutes)
        .toString()
        .shouldBe("2025-02-15T12:40:00Z")
    }
    test("1.hours") {
      instant.truncateTo(1.hours)
        .toString()
        .shouldBe("2025-02-15T12:00:00Z")
    }
    test("1.days") {
      instant.truncateTo(1.days)
        .toString()
        .shouldBe("2025-02-15T00:00:00Z")
    }
  }
})
