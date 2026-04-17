package build.wallet.time

import build.wallet.platform.settings.*
import build.wallet.platform.settings.LocaleProviderFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.TimeZone.Companion.UTC

class TimeZoneFormatterTests : FunSpec({
  val pacificTimeZone = TimeZone.AmericaLosAngeles
  val hkt = TimeZone.HongKong
  val hst = TimeZone.Hawaii
  val ist = TimeZone.India

  val clock = ClockFake()
  val localeProvider = LocaleProviderFake()
  val formatter = TimeZoneFormatterImpl(localeProvider)

  beforeTest {
    localeProvider.reset()
    clock.now = Instant.parse("2024-01-15T12:00:00Z")
  }

  test("PST time zone format in en-US during standard time") {
    localeProvider.locale = Locale.EN_US
    formatter.timeZoneShortNameWithHoursOffset(
      timeZone = pacificTimeZone,
      clock = clock
    ).shouldBe("PST (UTC -8)")
  }

  test("PST time zone format in fr-CA during standard time") {
    localeProvider.locale = Locale.FR_CA
    formatter.timeZoneShortNameWithHoursOffset(
      timeZone = pacificTimeZone,
      clock = clock
    ).shouldBe("HNP (UTC -8)")
  }

  test("PDT time zone format in en-US during daylight time") {
    clock.now = Instant.parse("2024-07-15T12:00:00Z")
    localeProvider.locale = Locale.EN_US
    formatter.timeZoneShortNameWithHoursOffset(
      timeZone = pacificTimeZone,
      clock = clock
    ).shouldBe("PDT (UTC -7)")
  }

  test("PDT time zone format in fr-CA during daylight time") {
    clock.now = Instant.parse("2024-07-15T12:00:00Z")
    localeProvider.locale = Locale.FR_CA
    formatter.timeZoneShortNameWithHoursOffset(
      timeZone = pacificTimeZone,
      clock = clock
    ).shouldBe("HAP (UTC -7)")
  }

  test("Pacific timezone offset in hours during standard time") {
    pacificTimeZone.hoursFromUtc(clock).shouldBe(-8)
  }

  test("Pacific timezone offset in HH:MM:SS during standard time") {
    pacificTimeZone.timeFromUtcInHms(clock).shouldBe("-08:00:00")
  }

  test("UTC timezone offset in HH:MM:SS") {
    UTC.timeFromUtcInHms(clock).shouldBe("+00:00:00")
  }

  test("HKT timezone offset in HH:MM:SS") {
    hkt.timeFromUtcInHms(clock).shouldBe("+08:00:00")
  }

  test("HST timezone offset in HH:MM:SS") {
    hst.timeFromUtcInHms(clock).shouldBe("-10:00:00")
  }

  test("IST timezone offset in HH:MM:SS") {
    ist.timeFromUtcInHms(clock).shouldBe("+05:30:00")
  }
})
