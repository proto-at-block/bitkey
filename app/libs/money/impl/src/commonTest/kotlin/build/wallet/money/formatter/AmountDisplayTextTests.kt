package build.wallet.money.formatter

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AmountDisplayTextTests : FunSpec({
  test("AmountDisplayText String representation is redacted") {
    AmountDisplayText(
      primaryAmountText = "foo",
      secondaryAmountText = "bar"
    ).toString().shouldBe("AmountDisplayText(██)")
  }
})
