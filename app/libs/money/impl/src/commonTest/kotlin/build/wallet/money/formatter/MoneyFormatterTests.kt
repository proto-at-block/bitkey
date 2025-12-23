package build.wallet.money.formatter

import build.wallet.amount.DoubleFormatterImpl
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.testJPY
import build.wallet.money.testKWD
import build.wallet.platform.settings.EN_US
import build.wallet.platform.settings.FR_FR
import build.wallet.platform.settings.Locale
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MoneyFormatterTests : FunSpec({

  var locale = Locale.EN_US
  lateinit var definitions: MoneyFormatterDefinitions

  beforeTest {
    definitions = MoneyFormatterDefinitionsImpl(
      doubleFormatter = DoubleFormatterImpl(
        localeProvider = { locale }
      )
    )
  }

  test("Formatting zero with currency with 2 fractional digits (USD)") {
    val zero = FiatMoney.zeroUsd()

    locale = Locale.EN_US
    definitions.fiatStandard.stringValue(zero).shouldBe("$0.00")
    definitions.fiatStandardWithSign.stringValue(zero).shouldBe("$0.00")
    definitions.fiatCompact.stringValue(zero).shouldBe("$0")

    locale = Locale.FR_FR
    definitions.fiatStandard.stringValue(zero).shouldBe("$0,00")
    definitions.fiatStandardWithSign.stringValue(zero).shouldBe("$0,00")
    definitions.fiatCompact.stringValue(zero).shouldBe("$0")
  }

  test("Formatting zero with currency with 0 fractional digits (JPY)") {
    val zero = FiatMoney.zero(testJPY)

    listOf(Locale.EN_US, Locale.FR_FR).forEach {
      locale = it
      definitions.fiatStandard.stringValue(zero).shouldBe("¥0")
      definitions.fiatStandardWithSign.stringValue(zero).shouldBe("¥0")
      definitions.fiatCompact.stringValue(zero).shouldBe("¥0")
      definitions.fiatStandard.stringValue(FiatMoney.zero(testJPY))
    }
  }

  test("Formatting zero with currency with 3 fractional digits (testKWD)") {
    val zero = FiatMoney.zero(testKWD)

    locale = Locale.EN_US
    definitions.fiatStandard.stringValue(zero).shouldBe("KWD0.000")
    definitions.fiatStandardWithSign.stringValue(zero).shouldBe("KWD0.000")
    definitions.fiatCompact.stringValue(zero).shouldBe("KWD0")

    locale = Locale.FR_FR
    definitions.fiatStandard.stringValue(zero).shouldBe("KWD0,000")
    definitions.fiatStandardWithSign.stringValue(zero).shouldBe("KWD0,000")
    definitions.fiatCompact.stringValue(zero).shouldBe("KWD0")
  }

  test("Formatting zero with currency with 8 fractional digits (BTC)") {
    val zero = BitcoinMoney.zero()

    locale = Locale.EN_US
    definitions.bitcoinCode.stringValue(zero).shouldBe("0.00000000 BTC")
    definitions.bitcoinReducedCode.stringValue(zero).shouldBe("0 BTC")
    definitions.bitcoinFractionalNameOnly.stringValue(zero).shouldBe("0 sats")

    locale = Locale.FR_FR
    definitions.bitcoinCode.stringValue(zero).shouldBe("0,00000000 BTC")
    definitions.bitcoinReducedCode.stringValue(zero).shouldBe("0 BTC")
    definitions.bitcoinFractionalNameOnly.stringValue(zero).shouldBe("0 sats")
  }

  test("Formatting fractional number with currency with 2 fractional digits (USD)") {
    val dollars = 1.1.toBigDecimal()

    locale = Locale.EN_US

    definitions.fiatStandard.stringValue(FiatMoney.usd(dollars)).shouldBe("$1.10")
    definitions.fiatStandard.stringValue(FiatMoney.usd(dollars.negate())).shouldBe("- $1.10")

    definitions.fiatStandardWithSign.stringValue(FiatMoney.usd(dollars)).shouldBe("+ $1.10")
    definitions.fiatStandardWithSign.stringValue(
      FiatMoney.usd(dollars.negate())
    ).shouldBe("- $1.10")

    definitions.fiatCompact.stringValue(FiatMoney.usd(dollars)).shouldBe("$1.10")
    definitions.fiatCompact.stringValue(FiatMoney.usd(dollars.negate())).shouldBe("- $1.10")

    locale = Locale.FR_FR

    definitions.fiatStandard.stringValue(FiatMoney.usd(dollars)).shouldBe("$1,10")
    definitions.fiatStandard.stringValue(FiatMoney.usd(dollars.negate())).shouldBe("- $1,10")

    definitions.fiatStandardWithSign.stringValue(FiatMoney.usd(dollars)).shouldBe("+ $1,10")
    definitions.fiatStandardWithSign.stringValue(
      FiatMoney.usd(dollars.negate())
    ).shouldBe("- $1,10")

    definitions.fiatCompact.stringValue(FiatMoney.usd(dollars)).shouldBe("$1,10")
    definitions.fiatCompact.stringValue(FiatMoney.usd(dollars.negate())).shouldBe("- $1,10")
  }

  test("Formatting whole number with currency with 2 fractional digits (USD)") {
    val dollars = 50.toBigDecimal()

    locale = Locale.EN_US

    definitions.fiatStandard.stringValue(FiatMoney.usd(dollars)).shouldBe("$50.00")
    definitions.fiatStandard.stringValue(FiatMoney.usd(dollars.negate())).shouldBe("- $50.00")

    definitions.fiatStandardWithSign.stringValue(FiatMoney.usd(dollars)).shouldBe("+ $50.00")
    definitions.fiatStandardWithSign.stringValue(
      FiatMoney.usd(dollars.negate())
    ).shouldBe("- $50.00")

    definitions.fiatCompact.stringValue(FiatMoney.usd(dollars)).shouldBe("$50")
    definitions.fiatCompact.stringValue(FiatMoney.usd(dollars.negate())).shouldBe("- $50")

    locale = Locale.FR_FR

    definitions.fiatStandard.stringValue(FiatMoney.usd(dollars)).shouldBe("$50,00")
    definitions.fiatStandard.stringValue(FiatMoney.usd(dollars.negate())).shouldBe("- $50,00")

    definitions.fiatStandardWithSign.stringValue(FiatMoney.usd(dollars)).shouldBe("+ $50,00")
    definitions.fiatStandardWithSign.stringValue(
      FiatMoney.usd(dollars.negate())
    ).shouldBe("- $50,00")

    definitions.fiatCompact.stringValue(FiatMoney.usd(dollars)).shouldBe("$50")
    definitions.fiatCompact.stringValue(FiatMoney.usd(dollars.negate())).shouldBe("- $50")
  }

  test("Formatting fractional positive number with currency with 0 fractional digits (JPY)") {
    val value = 1.1.toBigDecimal()

    listOf(Locale.EN_US, Locale.FR_FR).forEach {
      locale = it
      definitions.fiatStandard.stringValue(FiatMoney(testJPY, value)).shouldBe("¥1")
      definitions.fiatStandard.stringValue(FiatMoney(testJPY, value.negate())).shouldBe("- ¥1")

      definitions.fiatStandardWithSign.stringValue(FiatMoney(testJPY, value)).shouldBe("+ ¥1")
      definitions.fiatStandardWithSign.stringValue(
        FiatMoney(testJPY, value.negate())
      ).shouldBe("- ¥1")

      definitions.fiatCompact.stringValue(FiatMoney(testJPY, value)).shouldBe("¥1")
      definitions.fiatCompact.stringValue(FiatMoney(testJPY, value.negate())).shouldBe("- ¥1")
    }
  }

  test("Formatting whole positive number with currency with 0 fractional digits (JPY)") {
    val value = 5.toBigDecimal()

    listOf(Locale.EN_US, Locale.FR_FR).forEach {
      locale = it
      definitions.fiatStandard.stringValue(FiatMoney(testJPY, value)).shouldBe("¥5")
      definitions.fiatStandard.stringValue(FiatMoney(testJPY, value.negate())).shouldBe("- ¥5")

      definitions.fiatStandardWithSign.stringValue(FiatMoney(testJPY, value)).shouldBe("+ ¥5")
      definitions.fiatStandardWithSign.stringValue(
        FiatMoney(testJPY, value.negate())
      ).shouldBe("- ¥5")

      definitions.fiatCompact.stringValue(FiatMoney(testJPY, value)).shouldBe("¥5")
      definitions.fiatCompact.stringValue(FiatMoney(testJPY, value.negate())).shouldBe("- ¥5")
    }
  }

  test("Formatting fractional positive number with currency with 3 fractional digits (testKWD)") {
    val value = 1.1.toBigDecimal()

    locale = Locale.EN_US

    definitions.fiatStandard.stringValue(FiatMoney(testKWD, value)).shouldBe("KWD1.100")
    definitions.fiatStandard.stringValue(FiatMoney(testKWD, value.negate())).shouldBe("- KWD1.100")

    definitions.fiatStandardWithSign.stringValue(FiatMoney(testKWD, value)).shouldBe("+ KWD1.100")
    definitions.fiatStandardWithSign.stringValue(
      FiatMoney(testKWD, value.negate())
    ).shouldBe("- KWD1.100")

    definitions.fiatCompact.stringValue(FiatMoney(testKWD, value)).shouldBe("KWD1.100")
    definitions.fiatCompact.stringValue(FiatMoney(testKWD, value.negate())).shouldBe("- KWD1.100")

    locale = Locale.FR_FR

    definitions.fiatStandard.stringValue(FiatMoney(testKWD, value)).shouldBe("KWD1,100")
    definitions.fiatStandard.stringValue(FiatMoney(testKWD, value.negate())).shouldBe("- KWD1,100")

    definitions.fiatStandardWithSign.stringValue(FiatMoney(testKWD, value)).shouldBe("+ KWD1,100")
    definitions.fiatStandardWithSign.stringValue(
      FiatMoney(testKWD, value.negate())
    ).shouldBe("- KWD1,100")

    definitions.fiatCompact.stringValue(FiatMoney(testKWD, value)).shouldBe("KWD1,100")
    definitions.fiatCompact.stringValue(FiatMoney(testKWD, value.negate())).shouldBe("- KWD1,100")
  }

  test("Formatting whole positive number with currency with 3 fractional digits (testKWD)") {
    val value = 5.toBigDecimal()

    locale = Locale.EN_US

    definitions.fiatStandard.stringValue(FiatMoney(testKWD, value)).shouldBe("KWD5.000")
    definitions.fiatStandard.stringValue(FiatMoney(testKWD, value.negate())).shouldBe("- KWD5.000")

    definitions.fiatStandardWithSign.stringValue(FiatMoney(testKWD, value)).shouldBe("+ KWD5.000")
    definitions.fiatStandardWithSign.stringValue(
      FiatMoney(testKWD, value.negate())
    ).shouldBe("- KWD5.000")

    definitions.fiatCompact.stringValue(FiatMoney(testKWD, value)).shouldBe("KWD5")
    definitions.fiatCompact.stringValue(FiatMoney(testKWD, value.negate())).shouldBe("- KWD5")

    locale = Locale.FR_FR

    definitions.fiatStandard.stringValue(FiatMoney(testKWD, value)).shouldBe("KWD5,000")
    definitions.fiatStandard.stringValue(FiatMoney(testKWD, value.negate())).shouldBe("- KWD5,000")

    definitions.fiatStandardWithSign.stringValue(FiatMoney(testKWD, value)).shouldBe("+ KWD5,000")
    definitions.fiatStandardWithSign.stringValue(
      FiatMoney(testKWD, value.negate())
    ).shouldBe("- KWD5,000")

    definitions.fiatCompact.stringValue(FiatMoney(testKWD, value)).shouldBe("KWD5")
    definitions.fiatCompact.stringValue(FiatMoney(testKWD, value.negate())).shouldBe("- KWD5")
  }

  test("Formatting fractional positive number with currency with 8 fractional digits (BTC)") {
    val btc = 1.1.toBigDecimal()

    locale = Locale.EN_US

    definitions.bitcoinCode.stringValue(BitcoinMoney.btc(btc)).shouldBe("1.10000000 BTC")
    definitions.bitcoinCode.stringValue(BitcoinMoney.btc(btc.negate())).shouldBe("- 1.10000000 BTC")

    definitions.bitcoinReducedCode.stringValue(BitcoinMoney.btc(btc)).shouldBe("1.1 BTC")
    definitions.bitcoinReducedCode.stringValue(BitcoinMoney.btc(btc.negate())).shouldBe("- 1.1 BTC")

    definitions.bitcoinFractionalNameOnly.stringValue(
      BitcoinMoney.btc(btc)
    ).shouldBe("110,000,000 sats")
    definitions.bitcoinFractionalNameOnly.stringValue(
      BitcoinMoney.btc(btc.negate())
    ).shouldBe("- 110,000,000 sats")

    locale = Locale.FR_FR

    definitions.bitcoinCode.stringValue(BitcoinMoney.btc(btc)).shouldBe("1,10000000 BTC")
    definitions.bitcoinCode.stringValue(BitcoinMoney.btc(btc.negate())).shouldBe("- 1,10000000 BTC")

    definitions.bitcoinReducedCode.stringValue(BitcoinMoney.btc(btc)).shouldBe("1,1 BTC")
    definitions.bitcoinReducedCode.stringValue(BitcoinMoney.btc(btc.negate())).shouldBe("- 1,1 BTC")

    definitions.bitcoinFractionalNameOnly.stringValue(
      BitcoinMoney.btc(btc)
    ).shouldBe("110 000 000 sats")
    definitions.bitcoinFractionalNameOnly.stringValue(
      BitcoinMoney.btc(btc.negate())
    ).shouldBe("- 110 000 000 sats")
  }

  test("Formatting single satoshi uses singular unit name") {
    locale = Locale.EN_US

    definitions.bitcoinFractionalNameOnly.stringValue(
      BitcoinMoney.sats(1)
    ).shouldBe("1 sat")
  }

  test("Formatting satoshis with BIP 177 uses symbol prefix") {
    locale = Locale.EN_US

    definitions.bitcoinFractionalBip177.stringValue(
      BitcoinMoney.btc(1.1.toBigDecimal())
    ).shouldBe("₿110,000,000")

    definitions.bitcoinFractionalBip177.stringValue(
      BitcoinMoney.btc(1.1.toBigDecimal().negate())
    ).shouldBe("- ₿110,000,000")

    definitions.bitcoinFractionalBip177.stringValue(
      BitcoinMoney.sats(1)
    ).shouldBe("₿1")

    definitions.bitcoinFractionalBip177.stringValue(
      BitcoinMoney.sats(-1)
    ).shouldBe("- ₿1")

    locale = Locale.FR_FR

    definitions.bitcoinFractionalBip177.stringValue(
      BitcoinMoney.btc(1.1.toBigDecimal())
    ).shouldBe("₿110 000 000")

    definitions.bitcoinFractionalBip177.stringValue(
      BitcoinMoney.btc(1.1.toBigDecimal().negate())
    ).shouldBe("- ₿110 000 000")

    definitions.bitcoinFractionalBip177.stringValue(
      BitcoinMoney.sats(1)
    ).shouldBe("₿1")

    definitions.bitcoinFractionalBip177.stringValue(
      BitcoinMoney.sats(-1)
    ).shouldBe("- ₿1")
  }

  test("Formatting whole positive number with currency with 8 fractional digits (BTC)") {
    val btc = 5.toBigDecimal()

    locale = Locale.EN_US

    definitions.bitcoinCode.stringValue(BitcoinMoney.btc(btc)).shouldBe("5.00000000 BTC")
    definitions.bitcoinCode.stringValue(BitcoinMoney.btc(btc.negate())).shouldBe("- 5.00000000 BTC")

    definitions.bitcoinReducedCode.stringValue(BitcoinMoney.btc(btc)).shouldBe("5 BTC")
    definitions.bitcoinReducedCode.stringValue(BitcoinMoney.btc(btc.negate())).shouldBe("- 5 BTC")

    definitions.bitcoinFractionalNameOnly.stringValue(
      BitcoinMoney.btc(btc)
    ).shouldBe("500,000,000 sats")
    definitions.bitcoinFractionalNameOnly.stringValue(
      BitcoinMoney.btc(btc.negate())
    ).shouldBe("- 500,000,000 sats")

    locale = Locale.FR_FR

    definitions.bitcoinCode.stringValue(BitcoinMoney.btc(btc)).shouldBe("5,00000000 BTC")
    definitions.bitcoinCode.stringValue(BitcoinMoney.btc(btc.negate())).shouldBe("- 5,00000000 BTC")

    definitions.bitcoinReducedCode.stringValue(BitcoinMoney.btc(btc)).shouldBe("5 BTC")
    definitions.bitcoinReducedCode.stringValue(BitcoinMoney.btc(btc.negate())).shouldBe("- 5 BTC")

    definitions.bitcoinFractionalNameOnly.stringValue(
      BitcoinMoney.btc(btc)
    ).shouldBe("500 000 000 sats")
    definitions.bitcoinFractionalNameOnly.stringValue(
      BitcoinMoney.btc(btc.negate())
    ).shouldBe("- 500 000 000 sats")
  }
})
