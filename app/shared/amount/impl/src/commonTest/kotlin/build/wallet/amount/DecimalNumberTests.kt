package build.wallet.amount

import build.wallet.amount.Amount.DecimalNumber
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class DecimalNumberTests : FunSpec({

  test("Init requires non empty string") {
    shouldThrow<IllegalArgumentException> {
      DecimalNumber(numberString = "", maximumFractionDigits = 2, decimalSeparator = '.')
    }
  }

  test("Init requires no more than one decimal") {
    shouldThrow<IllegalArgumentException> {
      DecimalNumber(numberString = "1.1.1", maximumFractionDigits = 2, decimalSeparator = '.')
      DecimalNumber(numberString = "1,1,1", maximumFractionDigits = 2, decimalSeparator = ',')
    }
    shouldNotThrow<IllegalArgumentException> {
      DecimalNumber(numberString = "1.1", maximumFractionDigits = 2, decimalSeparator = '.')
      DecimalNumber(numberString = "1", maximumFractionDigits = 2, decimalSeparator = '.')

      DecimalNumber(numberString = "1,1", maximumFractionDigits = 2, decimalSeparator = ',')
      DecimalNumber(numberString = "1", maximumFractionDigits = 2, decimalSeparator = ',')
    }
  }

  test("Init requires only digits or decimals") {
    shouldThrow<IllegalArgumentException> {
      DecimalNumber(numberString = "abc", maximumFractionDigits = 2, decimalSeparator = '.')
      DecimalNumber(numberString = "abc", maximumFractionDigits = 2, decimalSeparator = ',')
    }
  }

  test("Init requires positive number for maximum fractional digits") {
    shouldThrow<IllegalArgumentException> {
      DecimalNumber(numberString = "1", maximumFractionDigits = 0, decimalSeparator = '.')
      DecimalNumber(numberString = "1", maximumFractionDigits = 0, decimalSeparator = ',')
    }
    shouldThrow<IllegalArgumentException> {
      DecimalNumber(numberString = "1", maximumFractionDigits = -1, decimalSeparator = '.')
      DecimalNumber(numberString = "1", maximumFractionDigits = -1, decimalSeparator = ',')
    }
  }
})
