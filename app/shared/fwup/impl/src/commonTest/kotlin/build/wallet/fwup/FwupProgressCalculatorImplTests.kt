package build.wallet.fwup

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FwupProgressCalculatorImplTests : FunSpec({

  val calculator = FwupProgressCalculatorImpl()

  test("0, 0 progress calculation") {
    calculator.calculateProgress(0U, 0U)
      .shouldBe(0f)
  }

  test("0, 1234 progress calculation") {
    calculator.calculateProgress(0U, 1234U)
      .shouldBe(0f)
  }

  test("111, 1234 progress calculation") {
    calculator.calculateProgress(111U, 1230U)
      .shouldBe(9.02f)
  }

  test("12345, 1234 progress calculation") {
    calculator.calculateProgress(12345U, 1230U)
      .shouldBe(100f)
  }
})
