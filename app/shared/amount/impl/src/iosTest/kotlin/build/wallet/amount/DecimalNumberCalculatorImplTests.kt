package build.wallet.amount

import build.wallet.amount.Amount.Companion.MAXIMUM
import build.wallet.amount.Amount.DecimalNumber
import build.wallet.platform.settings.TestLocale
import build.wallet.platform.settings.TestLocale.EN_US
import build.wallet.platform.settings.TestLocale.FR_FR
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import platform.Foundation.NSLocale

class DecimalNumberCalculatorImplTests : FunSpec({

  test("0 add 4 results in 4") {
    listOf(EN_US, FR_FR).forEach {
      with(it) {
        calculator().append(amount = decimalNumber("0"), digit = 4).numberString
          .shouldBe("4")
      }
    }
  }

  test("0. add 4 results in 0.4") {
    with(EN_US) {
      calculator().append(amount = decimalNumber("0."), digit = 4).numberString
        .shouldBe("0.4")
    }
    with(FR_FR) {
      calculator().append(amount = decimalNumber("0,"), digit = 4).numberString
        .shouldBe("0,4")
    }
  }

  test("0 delete results in 0") {
    listOf(EN_US, FR_FR).forEach {
      with(it) {
        calculator().delete(amount = decimalNumber("0")).numberString
          .shouldBe("0")
      }
    }
  }

  test("4 add 2 results in 42") {
    listOf(EN_US, FR_FR).forEach {
      with(it) {
        calculator().append(amount = decimalNumber("4"), digit = 2).numberString
          .shouldBe("42")
      }
    }
  }

  test("4.15 with max 2 fraction digits add 2 results in 4.15") {
    with(EN_US) {
      calculator().append(amount = decimalNumber("4.15", 2), digit = 2).numberString
        .shouldBe("4.15")
    }
    with(FR_FR) {
      calculator().append(amount = decimalNumber("4,15", 2), digit = 2).numberString
        .shouldBe("4,15")
    }
  }

  test("4 decimal results in 4.") {
    with(EN_US) {
      calculator().decimal(amount = decimalNumber("4")).numberString
        .shouldBe("4.")
    }
    with(FR_FR) {
      calculator().decimal(amount = decimalNumber("4")).numberString
        .shouldBe("4,")
    }
  }

  test("4. decimal results in 4.") {
    with(EN_US) {
      calculator().decimal(amount = decimalNumber("4")).numberString
        .shouldBe("4.")
    }
    with(FR_FR) {
      calculator().decimal(amount = decimalNumber("4,")).numberString
        .shouldBe("4,")
    }
  }

  test("4. add 2 results in 4.2 in en-US") {
    with(EN_US) {
      calculator().append(amount = decimalNumber("4."), digit = 2).numberString
        .shouldBe("4.2")
    }
    with(FR_FR) {
      calculator().append(amount = decimalNumber("4,"), digit = 2).numberString
        .shouldBe("4,2")
    }
  }

  test("42 delete results in 4") {
    listOf(EN_US, FR_FR).forEach {
      with(it) {
        calculator().delete(amount = decimalNumber("42")).numberString
          .shouldBe("4")
      }
    }
  }

  test("4.2 delete results in 4.") {
    with(EN_US) {
      calculator().delete(amount = decimalNumber("4.2")).numberString
        .shouldBe("4.")
    }
    with(FR_FR) {
      calculator().delete(amount = decimalNumber("4,2")).numberString
        .shouldBe("4,")
    }
  }

  test("4. delete results in 4") {
    with(EN_US) {
      calculator().delete(amount = decimalNumber("4.")).numberString
        .shouldBe("4")
    }
    with(FR_FR) {
      calculator().delete(amount = decimalNumber("4,")).numberString
        .shouldBe("4")
    }
  }

  test("9999.9999999 add 9 results in 9999.99999999") {
    with(EN_US) {
      calculator().append(amount = decimalNumber("9999.9999999", 8), 9).numberString
        .shouldBe("9999.99999999")
    }
    with(FR_FR) {
      calculator().append(amount = decimalNumber("9999,9999999", 8), 9).numberString
        .shouldBe("9999,99999999")
    }
  }

  test("0.00000001 delete results in 0.0000000") {
    with(EN_US) {
      calculator().delete(amount = decimalNumber("0.00000001", 8)).numberString
        .shouldBe("0.0000000")
    }
    with(FR_FR) {
      calculator().delete(amount = decimalNumber("0,00000001", 8)).numberString
        .shouldBe("0,0000000")
    }
  }

  test("MAXIMUM add results in MAXIMUM (avoids overflow error)") {
    listOf(EN_US, FR_FR).forEach {
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

private fun TestLocale.decimalNumber(
  numberString: String,
  maximumFractionDigits: Int = 2,
) = DecimalNumber(
  numberString = numberString,
  maximumFractionDigits = maximumFractionDigits,
  decimalSeparator =
    when (this) {
      EN_US -> '.'
      FR_FR -> ','
    }
)

private fun TestLocale.calculator(): DecimalNumberCalculatorImpl {
  val localeProvider = { NSLocale(localeIdentifier = iosLocaleIdentifier()) }
  val decimalSeparatorProvider =
    DecimalSeparatorProviderImpl(
      localeProvider = localeProvider
    )
  val doubleFormatter =
    DoubleFormatterImpl(
      localeProvider = localeProvider
    )
  return DecimalNumberCalculatorImpl(
    decimalNumberCreator =
      DecimalNumberCreatorImpl(
        decimalSeparatorProvider = decimalSeparatorProvider,
        doubleFormatter = doubleFormatter
      ),
    decimalSeparatorProvider = decimalSeparatorProvider,
    doubleFormatter = doubleFormatter
  )
}
