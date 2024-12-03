package build.wallet.money.formatter

import build.wallet.amount.Amount.DecimalNumber
import build.wallet.amount.Amount.WholeNumber
import build.wallet.amount.DoubleFormatterImpl
import build.wallet.amount.decimalSeparator
import build.wallet.money.currency.BTC
import build.wallet.money.currency.USD
import build.wallet.money.input.MoneyInputFormatter
import build.wallet.money.input.MoneyInputFormatterImpl
import build.wallet.platform.settings.FR_FR
import build.wallet.platform.settings.Locale
import build.wallet.platform.settings.LocaleProviderFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class MoneyInputFormatterImplTests : FunSpec({

  val localeProvider = LocaleProviderFake()

  lateinit var inputFormatter: MoneyInputFormatter

  fun decimal(
    numberString: String,
    maximumFractionDigits: Int = 2,
  ) = DecimalNumber(
    numberString = numberString,
    maximumFractionDigits = maximumFractionDigits,
    decimalSeparator = localeProvider.currentLocale().decimalSeparator
  )

  beforeTest {
    localeProvider.reset()
    val doubleFormatter = DoubleFormatterImpl(localeProvider = localeProvider)
    inputFormatter = MoneyInputFormatterImpl(
      localeProvider = localeProvider,
      doubleFormatter = doubleFormatter,
      moneyFormatterDefinitions = MoneyFormatterDefinitionsImpl(
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
    localeProvider.locale = Locale.FR_FR
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
