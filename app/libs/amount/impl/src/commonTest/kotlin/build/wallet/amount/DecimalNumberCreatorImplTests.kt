package build.wallet.amount

import build.wallet.platform.settings.LocaleProviderFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DecimalNumberCreatorImplTests : FunSpec({

  val localeProvider = LocaleProviderFake()
  val creator = DecimalNumberCreatorImpl(
    localeProvider = localeProvider,
    doubleFormatter = DoubleFormatterImpl(localeProvider)
  )

  test("Create truncates fractional digits") {
    creator.create(numberString = "26.789", maximumFractionDigits = 2).numberString
      .shouldBe("26.78")
  }

  test("Create with number truncates trailing zeroes") {
    creator.create(number = 0.00, maximumFractionDigits = 2).numberString
      .shouldBe("0")
    creator.create(number = 10.00, maximumFractionDigits = 2).numberString
      .shouldBe("10")
  }

  test("Create with number does not truncate fractional numbers") {
    creator.create(number = 0.15, maximumFractionDigits = 2).numberString
      .shouldBe("0.15")
    creator.create(number = 1.15, maximumFractionDigits = 2).numberString
      .shouldBe("1.15")
  }
})
