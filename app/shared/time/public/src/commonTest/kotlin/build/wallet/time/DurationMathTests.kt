package build.wallet.time

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.shouldBeZero
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class DurationMathTests : FunSpec({

  val now = someInstant

  context("remaining duration") {
    test("calculate remaining duration - some time left") {
      nonNegativeDurationBetween(
        startTime = now,
        endTime = now + 1.hours
      ).shouldBe(1.hours)
    }

    test("calculate remaining duration - no time left") {
      nonNegativeDurationBetween(
        startTime = now,
        endTime = now
      ).shouldBe(Duration.ZERO)
    }

    test("calculate remaining duration - end time is before now") {
      nonNegativeDurationBetween(
        startTime = now,
        endTime = now - 1.hours
      ).shouldBe(Duration.ZERO)
    }
  }

  context("duration progress") {
    test("0% progress") {
      durationProgress(
        now = now,
        startTime = now,
        endTime = now + 100.seconds
      ).shouldBeZero()
    }

    test("42% progress") {
      durationProgress(
        now = now,
        startTime = now - 42.seconds,
        endTime = now + 58.seconds
      ).shouldBe(0.42f)
    }

    test("50% progress") {
      durationProgress(
        now = now,
        startTime = now - 50.seconds,
        endTime = now + 50.seconds
      ).shouldBe(0.50f)
    }

    test("99% progress") {
      durationProgress(
        now = now,
        startTime = now - 99.seconds,
        endTime = now + 1.seconds
      ).shouldBe(0.99f)
    }

    test("100% progress") {
      durationProgress(
        now = now,
        startTime = now - 100.seconds,
        endTime = now
      ).shouldBe(1f)
    }

    test("0% progress past time") {
      durationProgress(
        now = now,
        startTime = now + 5.seconds,
        endTime = now + 10.seconds
      ).shouldBeZero()
    }

    test("100% progress future time") {
      durationProgress(
        now = now,
        startTime = now - 100.seconds,
        endTime = now - 50.seconds
      ).shouldBe(1f)
    }
  }
})
