package build.wallet.sqldelight.adapter

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant

class InstantAsEpochMillisecondsColumnAdapterTests : FunSpec({

  val adapter = InstantAsEpochMillisecondsColumnAdapter
  val epochMilliseconds = 1673338945736L
  val instant =
    LocalDateTime(
      year = 2023,
      monthNumber = 1,
      dayOfMonth = 10,
      hour = 8,
      minute = 22,
      second = 25,
      nanosecond = 736_000_000
    ).toInstant(offset = UtcOffset.ZERO)

  test("encode Instant as epoch milliseconds") {
    adapter.encode(instant).shouldBe(epochMilliseconds)
  }

  test("decode epoch milliseconds as Instant") {
    adapter.decode(epochMilliseconds).shouldBe(instant)
  }
})
