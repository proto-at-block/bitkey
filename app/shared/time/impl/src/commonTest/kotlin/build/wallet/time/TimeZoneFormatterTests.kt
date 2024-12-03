package build.wallet.time

import build.wallet.platform.settings.*
import build.wallet.platform.settings.LocaleProviderFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.TimeZone
import kotlinx.datetime.TimeZone.Companion.UTC

class TimeZoneFormatterTests : FunSpec({
  val pdt = TimeZone.AmericaLosAngeles
  val hkt = TimeZone.HongKong
  val hst = TimeZone.Hawaii
  val ist = TimeZone.India

  val clock = ClockFake()
  val localeProvider = LocaleProviderFake()
  val formatter = TimeZoneFormatterImpl(localeProvider)

  beforeTest {
    localeProvider.reset()
  }

  test("PDT time zone format in en-US") {
    localeProvider.locale = Locale.EN_US
    formatter.timeZoneShortNameWithHoursOffset(
      timeZone = pdt,
      clock = clock
    ).shouldBe("PDT (UTC -8)")
  }

  test("PDT time zone format in fr-CA") {
    localeProvider.locale = Locale.FR_CA
    formatter.timeZoneShortNameWithHoursOffset(
      timeZone = pdt,
      clock = clock
    ).shouldBe("HAP (UTC -8)")
  }

  test("PDT timezone offset in hours") {
    pdt.hoursFromUtc(clock).shouldBe(-8)
  }

  test("PDT timezone offset in HH:MM:SS") {
    pdt.timeFromUtcInHms(clock).shouldBe("-08:00:00")
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
