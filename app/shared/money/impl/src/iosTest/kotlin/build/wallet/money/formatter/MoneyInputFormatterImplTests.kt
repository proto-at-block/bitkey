package build.wallet.money.formatter

import build.wallet.amount.Amount.DecimalNumber
import build.wallet.amount.Amount.WholeNumber
import build.wallet.amount.DecimalSeparatorProviderFake
import build.wallet.amount.DoubleFormatterImpl
import build.wallet.money.currency.BTC
import build.wallet.money.currency.USD
import build.wallet.money.formatter.internal.MoneyFormatterDefinitionsImpl
import build.wallet.money.input.MoneyInputFormatter
import build.wallet.money.input.MoneyInputFormatterImpl
import build.wallet.platform.settings.TestLocale.EN_US
import build.wallet.platform.settings.TestLocale.FR_FR
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import platform.Foundation.NSLocale

class MoneyInputFormatterImplTests : FunSpec({

  var locale = EN_US
  val localeProvider = { NSLocale(localeIdentifier = locale.iosLocaleIdentifier()) }

  lateinit var decimalSeparatorProvider: DecimalSeparatorProviderFake
  lateinit var inputFormatter: MoneyInputFormatter

  fun decimal(
    numberString: String,
    maximumFractionDigits: Int = 2,
  ) = DecimalNumber(
    numberString = numberString,
    maximumFractionDigits = maximumFractionDigits,
    decimalSeparator = decimalSeparatorProvider.decimalSeparator
  )

  beforeTest {
    locale = EN_US
    decimalSeparatorProvider = DecimalSeparatorProviderFake()
    val doubleFormatter = DoubleFormatterImpl(localeProvider = localeProvider)
    inputFormatter =
      MoneyInputFormatterImpl(
        decimalSeparatorProvider = decimalSeparatorProvider,
        doubleFormatter = doubleFormatter,
        moneyFormatterDefinitions =
          MoneyFormatterDefinitionsImpl(
            doubleFormatter = doubleFormatter
          )
      )
  }

  test("Zero whole number amount formatting, no ghosted substring") {
    val displayText =
      inputFormatter.displayText(
        inputAmount = WholeNumber(number = 0),
        inputAmountCurrency = BTC
      )

    displayText.displayText.shouldBe("0 sats")
    displayText.displayTextGhostedSubstring.shouldBeNull()
  }

  test("Non-zero whole number amount formatting, no ghosted substring") {
    val displayText =
      inputFormatter.displayText(
        inputAmount = WholeNumber(number = 2500),
        inputAmountCurrency = BTC
      )

    displayText.displayText.shouldBe("2,500 sats")
    displayText.displayTextGhostedSubstring.shouldBeNull()
  }

  test("Zero decimal number amount formatting, no ghosted substring") {
    val displayText =
      inputFormatter.displayText(
        inputAmount = decimal("0"),
        inputAmountCurrency = USD
      )

    displayText.displayText.shouldBe("$0")
    displayText.displayTextGhostedSubstring.shouldBeNull()
  }

  test("Non-zero decimal number amount formatting, no ghosted substring") {
    val displayText =
      inputFormatter.displayText(
        inputAmount = decimal("10"),
        inputAmountCurrency = USD
      )

    displayText.displayText.shouldBe("$10")
    displayText.displayTextGhostedSubstring.shouldBeNull()
  }

  test("Non-zero decimal number amount formatting with decimal, ghosted substring") {
    val displayText =
      inputFormatter.displayText(
        inputAmount = decimal("10."),
        inputAmountCurrency = USD
      )

    displayText.displayText.shouldBe("$10.00")
    displayText.displayTextGhostedSubstring?.string.shouldBe("00")
    displayText.displayTextGhostedSubstring?.range.shouldBe(4..5)
  }

  test(
    "Non-zero decimal number amount formatting with decimal with fr_FR locale, ghosted substring"
  ) {
    decimalSeparatorProvider.decimalSeparator = ','
    locale = FR_FR
    val displayText =
      inputFormatter.displayText(
        inputAmount = decimal("10,"),
        inputAmountCurrency = USD
      )

    displayText.displayText.shouldBe("$10,00")
    displayText.displayTextGhostedSubstring?.string.shouldBe("00")
    displayText.displayTextGhostedSubstring?.range.shouldBe(4..5)
  }

  test("Non-zero decimal number amount formatting with decimal, ghosted substring, BTC") {
    val displayText =
      inputFormatter.displayText(
        inputAmount = decimal("1.0", maximumFractionDigits = 8),
        inputAmountCurrency = BTC
      )

    displayText.displayText.shouldBe("1.00000000 BTC")
    displayText.displayTextGhostedSubstring?.string.shouldBe("0000000")
    displayText.displayTextGhostedSubstring?.range.shouldBe(3..9)
  }

  test("Non-zero decimal number amount formatting with decimal and zero, ghosted substring") {
    val displayText =
      inputFormatter.displayText(
        inputAmount = decimal("10.0", maximumFractionDigits = 2),
        inputAmountCurrency = USD
      )

    displayText.displayText.shouldBe("$10.00")
    displayText.displayTextGhostedSubstring?.string.shouldBe("0")
    displayText.displayTextGhostedSubstring?.range.shouldBe(5..5)
  }

  test(
    "Non-zero decimal number amount formatting with max fractional digits, no ghosted substring"
  ) {
    val displayText =
      inputFormatter.displayText(
        inputAmount = decimal("10.00", maximumFractionDigits = 2),
        inputAmountCurrency = USD
      )

    displayText.displayText.shouldBe("$10.00")
    displayText.displayTextGhostedSubstring.shouldBeNull()
  }

  test("Large non-zero decimal number amount formatting with decimal, ghosted substring") {
    val displayText =
      inputFormatter.displayText(
        inputAmount = decimal("1000.", maximumFractionDigits = 2),
        inputAmountCurrency = USD
      )

    displayText.displayText.shouldBe("$1,000.00")
    displayText.displayTextGhostedSubstring?.string.shouldBe("00")
    displayText.displayTextGhostedSubstring?.range.shouldBe(7..8)
  }
})
