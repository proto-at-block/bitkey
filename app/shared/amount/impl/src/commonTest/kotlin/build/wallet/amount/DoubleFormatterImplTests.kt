package build.wallet.amount

import build.wallet.platform.settings.EN_US
import build.wallet.platform.settings.FR_FR
import build.wallet.platform.settings.Locale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DoubleFormatterImplTests : FunSpec({
  var locale: Locale = Locale.EN_US
  val formatter = DoubleFormatterImpl(localeProvider = { locale })

  test("Format number to string") {
    fun Double.format(): String {
      return formatter.format(
        double = this,
        minimumFractionDigits = 2,
        maximumFractionDigits = 2,
        isGroupingUsed = true
      )
    }

    locale = Locale.EN_US
    0.0.format().shouldBe("0.00")
    5.6.format().shouldBe("5.60")
    12345.0.format().shouldBe("12,345.00")
    78.12345.format().shouldBe("78.12")

    locale = Locale.FR_FR
    0.0.format().shouldBe("0,00")
    5.6.format().shouldBe("5,60")
    12345.0.format().shouldBe("12 345,00")
    78.12345.format().shouldBe("78,12")
  }

  test("Parse number from string") {
    fun String.parse(): Double? {
      return formatter.parse(this)
    }

    locale = Locale.EN_US
    "0".parse().shouldBe(0.0)
    "5.6".parse().shouldBe(5.6)
    "12345".parse().shouldBe(12345.0)
    "78.12".parse().shouldBe(78.12)

    locale = Locale.FR_FR
    "0".parse().shouldBe(0.0)
    "5,6".parse().shouldBe(5.6)
    "12345".parse().shouldBe(12345.0)
    "78,12".parse().shouldBe(78.12)
  }
})
