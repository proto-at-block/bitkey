package build.wallet.amount

import build.wallet.platform.settings.LocaleIdentifierProviderFake
import build.wallet.platform.settings.TestLocale.EN_US
import build.wallet.platform.settings.TestLocale.FR_FR
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DoubleFormatterImplTests : FunSpec({

  val localeIdentifierProvider = LocaleIdentifierProviderFake()
  val formatter =
    DoubleFormatterImpl(
      localeIdentifierProvider = localeIdentifierProvider
    )

  test("Format number to string") {
    fun Double.format(): String {
      return formatter.format(
        double = this,
        minimumFractionDigits = 2,
        maximumFractionDigits = 2,
        isGroupingUsed = true
      )
    }

    localeIdentifierProvider.locale = EN_US
    0.0.format().shouldBe("0.00")
    5.6.format().shouldBe("5.60")
    12345.0.format().shouldBe("12,345.00")
    78.12345.format().shouldBe("78.12")

    localeIdentifierProvider.locale = FR_FR
    0.0.format().shouldBe("0,00")
    5.6.format().shouldBe("5,60")
    12345.0.format().shouldBe("12 345,00")
    78.12345.format().shouldBe("78,12")
  }

  test("Parse number from string") {
    fun String.parse(): Double? {
      return formatter.parse(this)
    }

    localeIdentifierProvider.locale = EN_US
    "0".parse().shouldBe(0.0)
    "5.6".parse().shouldBe(5.6)
    "12345".parse().shouldBe(12345.0)
    "78.12".parse().shouldBe(78.12)

    localeIdentifierProvider.locale = FR_FR
    "0".parse().shouldBe(0.0)
    "5,6".parse().shouldBe(5.6)
    "12345".parse().shouldBe(12345.0)
    "78,12".parse().shouldBe(78.12)
  }
})
