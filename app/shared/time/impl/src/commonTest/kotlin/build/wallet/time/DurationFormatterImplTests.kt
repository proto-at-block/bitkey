package build.wallet.time

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit.SECONDS

class DurationFormatterImplTests : FunSpec({
  val formatter = DurationFormatterImpl()

  test("Words: 14 days") {
    formatter.formatWithWords(14.days).shouldBe("14 days")
    formatter.formatWithWords(14.days.toDouble(SECONDS)).shouldBe("14 days")
  }

  test("Words: 8 days, 17 hours") {
    formatter.formatWithWords(8.days + 17.hours).shouldBe("8 days, 17 hours")
    formatter.formatWithWords((8.days + 17.hours).toDouble(SECONDS)).shouldBe("8 days, 17 hours")
  }

  test("Words: 2 hours, 5 minutes") {
    formatter.formatWithWords(2.hours + 5.minutes).shouldBe("2 hours, 5 minutes")
    formatter.formatWithWords((2.hours + 5.minutes).toDouble(SECONDS)).shouldBe(
      "2 hours, 5 minutes"
    )
  }

  test("Words: 42 minutes") {
    formatter.formatWithWords(42.minutes).shouldBe("42 minutes")
    formatter.formatWithWords(42.minutes.toDouble(SECONDS)).shouldBe("42 minutes")
  }

  test("Words: Less than 1 minute") {
    formatter.formatWithWords(42.seconds).shouldBe("Less than 1 minute")
    formatter.formatWithWords(42.seconds.toDouble(SECONDS)).shouldBe("Less than 1 minute")
  }

  test("MMSS: 1 min 2 seconds") {
    formatter.formatWithMMSS(1.minutes + 2.seconds).shouldBe("01:02")
  }

  test("MMSS: 25 seconds") {
    formatter.formatWithMMSS(25.seconds).shouldBe("00:25")
  }

  test("Alphabets: 14 days") {
    formatter.formatWithAlphabet(14.days).shouldBe("14d")
  }

  test("Alphabets: 8 days, 17 hours") {
    formatter.formatWithAlphabet(8.days + 17.hours).shouldBe("8d 17h")
  }

  test("Alphabets: 2 hours, 5 minutes") {
    formatter.formatWithAlphabet(2.hours + 5.minutes).shouldBe("2h 5m")
  }

  test("Alphabets: 42 minutes") {
    formatter.formatWithAlphabet(42.minutes).shouldBe("42m")
  }

  test("Alphabets: Less than 1 minute") {
    formatter.formatWithAlphabet(42.seconds).shouldBe("<1m")
  }
})
