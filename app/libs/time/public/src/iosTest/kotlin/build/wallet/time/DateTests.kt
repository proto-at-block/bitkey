package build.wallet.time

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DateTests : FunSpec({

  test("Instant to Date to Instant") {
    Instant(someInstant.toDate()).shouldBe(someInstant)
  }
})
