package build.wallet.amount

import build.wallet.amount.Amount.Companion.MAXIMUM
import build.wallet.platform.settings.EN_US
import build.wallet.platform.settings.FR_FR
import build.wallet.platform.settings.Locale
import build.wallet.platform.settings.LocaleProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DecimalNumberCalculatorImplTests : FunSpec({

  test("0 add 4 results in 4") {
    listOf(Locale.EN_US, Locale.FR_FR).forEach {
      with(it) {
        calculator().append(amount = decimalNumber("0"), digit = 4).numberString
          .shouldBe("4")
      }
    }
  }

  test("0. add 4 results in 0.4") {
    with(Locale.EN_US) {
      calculator().append(amount = decimalNumber("0."), digit = 4).numberString
        .shouldBe("0.4")
    }
    with(Locale.FR_FR) {
      calculator().append(amount = decimalNumber("0,"), digit = 4).numberString
        .shouldBe("0,4")
    }
  }

  test("0 delete results in 0") {
    listOf(Locale.EN_US, Locale.FR_FR).forEach {
      with(it) {
        calculator().delete(amount = decimalNumber("0")).numberString
          .shouldBe("0")
      }
    }
  }

  test("4 add 2 results in 42") {
    listOf(Locale.EN_US, Locale.FR_FR).forEach {
      with(it) {
        calculator().append(amount = decimalNumber("4"), digit = 2).numberString
          .shouldBe("42")
      }
    }
  }

  test("4.15 with max 2 fraction digits add 2 results in 4.15") {
    with(Locale.EN_US) {
      calculator().append(amount = decimalNumber("4.15", 2), digit = 2).numberString
        .shouldBe("4.15")
    }
    with(Locale.FR_FR) {
      calculator().append(amount = decimalNumber("4,15", 2), digit = 2).numberString
        .shouldBe("4,15")
    }
  }

  test("4 decimal results in 4.") {
    with(Locale.EN_US) {
      calculator().decimal(amount = decimalNumber("4")).numberString
        .shouldBe("4.")
    }
    with(Locale.FR_FR) {
      calculator().decimal(amount = decimalNumber("4")).numberString
        .shouldBe("4,")
    }
  }

  test("4. decimal results in 4.") {
    with(Locale.EN_US) {
      calculator().decimal(amount = decimalNumber("4")).numberString
        .shouldBe("4.")
    }
    with(Locale.FR_FR) {
      calculator().decimal(amount = decimalNumber("4,")).numberString
        .shouldBe("4,")
    }
  }

  test("4. add 2 results in 4.2 in en-US") {
    with(Locale.EN_US) {
      calculator().append(amount = decimalNumber("4."), digit = 2).numberString
        .shouldBe("4.2")
    }
    with(Locale.FR_FR) {
      calculator().append(amount = decimalNumber("4,"), digit = 2).numberString
        .shouldBe("4,2")
    }
  }

  test("42 delete results in 4") {
    listOf(Locale.EN_US, Locale.FR_FR).forEach {
      with(it) {
        calculator().delete(amount = decimalNumber("42")).numberString
          .shouldBe("4")
      }
    }
  }

  test("4.2 delete results in 4.") {
    with(Locale.EN_US) {
      calculator().delete(amount = decimalNumber("4.2")).numberString
        .shouldBe("4.")
    }
    with(Locale.FR_FR) {
      calculator().delete(amount = decimalNumber("4,2")).numberString
        .shouldBe("4,")
    }
  }

  test("4. delete results in 4") {
    with(Locale.EN_US) {
      calculator().delete(amount = decimalNumber("4.")).numberString
        .shouldBe("4")
    }
    with(Locale.FR_FR) {
      calculator().delete(amount = decimalNumber("4,")).numberString
        .shouldBe("4")
    }
  }

  test("9999.9999999 add 9 results in 9999.99999999") {
    with(Locale.EN_US) {
      calculator().append(amount = decimalNumber("9999.9999999", 8), 9).numberString
        .shouldBe("9999.99999999")
    }
    with(Locale.FR_FR) {
      calculator().append(amount = decimalNumber("9999,9999999", 8), 9).numberString
        .shouldBe("9999,99999999")
    }
  }

  test("0.00000001 delete results in 0.0000000") {
    with(Locale.EN_US) {
      calculator().delete(amount = decimalNumber("0.00000001", 8)).numberString
        .shouldBe("0.0000000")
    }
    with(Locale.FR_FR) {
      calculator().delete(amount = decimalNumber("0,00000001", 8)).numberString
        .shouldBe("0,0000000")
    }
  }

  test("MAXIMUM add results in MAXIMUM (avoids overflow error)") {
    listOf(Locale.EN_US, Locale.FR_FR).forEach {
      with(it) {
        val calculator = calculator()

        val amount1 = calculator.append(amount = decimalNumber(MAXIMUM.toString()), 9)
        val amount2 = calculator.append(amount = amount1, 9)
        val amount3 = calculator.append(amount = amount2, 9)

        amount3.numberString.shouldBe(MAXIMUM.toString())
      }
    }
  }
})

private fun Locale.decimalNumber(
  numberString: String,
  maximumFractionDigits: Int = 2,
) = Amount.DecimalNumber(
  numberString = numberString,
  maximumFractionDigits = maximumFractionDigits,
  decimalSeparator = decimalSeparator
)

private fun Locale.calculator(): DecimalNumberCalculatorImpl {
  val localeProvider = LocaleProvider { this }
  val doubleFormatter = DoubleFormatterImpl(localeProvider)
  return DecimalNumberCalculatorImpl(
    localeProvider = localeProvider,
    decimalNumberCreator = DecimalNumberCreatorImpl(
      localeProvider = localeProvider,
      doubleFormatter = doubleFormatter
    ),
    doubleFormatter = doubleFormatter
  )
}
