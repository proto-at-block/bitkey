package build.wallet.amount

import build.wallet.amount.Amount.Companion.MAXIMUM
import build.wallet.platform.settings.TestLocale.EN_US
import build.wallet.platform.settings.TestLocale.FR_FR
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WholeNumberCalculatorImplTests : FunSpec({
  val calculator = WholeNumberCalculatorImpl()

  test("0 add 4 results in 4") {
    calculator.append(amount = wholeNumber(0), digit = 4).shouldBe(wholeNumber(4))
  }

  test("0 delete results in 0") {
    calculator.delete(amount = wholeNumber(0)).shouldBe(wholeNumber(0))
  }

  test("4 add 2 results in 42") {
    calculator.append(amount = wholeNumber(4), digit = 2).shouldBe(wholeNumber(42))
  }

  test("42 delete results in 4") {
    calculator.delete(amount = wholeNumber(42)).shouldBe(wholeNumber(4))
  }

  test("MAXIMUM add results in MAXIMUM (avoids overflow error)") {
    listOf(EN_US, FR_FR).forEach {
      with(it) {
        val amount1 = calculator.append(amount = wholeNumber(MAXIMUM), 9)
        val amount2 = calculator.append(amount = amount1, 9)
        val amount3 = calculator.append(amount = amount2, 9)

        amount3.number.shouldBe(MAXIMUM)
      }
    }
  }
})

private fun wholeNumber(number: Long) = Amount.WholeNumber(number)
