package build.wallet.money.formatter

import build.wallet.amount.DoubleFormatterImpl
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.EUR
import build.wallet.money.currency.GBP
import build.wallet.money.currency.USD
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryMock
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.money.formatter.internal.MoneyDisplayFormatterImpl
import build.wallet.money.formatter.internal.MoneyFormatterDefinitionsImpl
import build.wallet.money.testAUD
import build.wallet.money.testCAD
import build.wallet.money.testJPY
import build.wallet.money.testKWD
import build.wallet.platform.settings.LocaleIdentifierProviderFake
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MoneyDisplayFormatterImplTests : FunSpec({

  val bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryMock()
  val localeIdentifierProvider = LocaleIdentifierProviderFake()
  val formatter =
    MoneyDisplayFormatterImpl(
      bitcoinDisplayPreferenceRepository = bitcoinDisplayPreferenceRepository,
      moneyFormatterDefinitions =
        MoneyFormatterDefinitionsImpl(
          doubleFormatter =
            DoubleFormatterImpl(
              localeIdentifierProvider = localeIdentifierProvider
            )
        )
    )

  test("Format standard fiat") {
    val value = 1.toBigDecimal()

    formatter.format(FiatMoney(testAUD, value)).shouldBe("$1.00")
    formatter.format(FiatMoney(testCAD, value)).shouldBe("$1.00")
    formatter.format(FiatMoney(EUR, value)).shouldBe("€1.00")
    formatter.format(FiatMoney(GBP, value)).shouldBe("£1.00")
    formatter.format(FiatMoney(testJPY, value)).shouldBe("¥1")
    formatter.format(FiatMoney(testKWD, value)).shouldBe("KWD1.000")
    formatter.format(FiatMoney(USD, value)).shouldBe("$1.00")
  }

  test("Format compact fiat") {
    val value = 1.toBigDecimal()

    formatter.formatCompact(FiatMoney(testAUD, value)).shouldBe("$1")
    formatter.formatCompact(FiatMoney(testCAD, value)).shouldBe("$1")
    formatter.formatCompact(FiatMoney(EUR, value)).shouldBe("€1")
    formatter.formatCompact(FiatMoney(GBP, value)).shouldBe("£1")
    formatter.formatCompact(FiatMoney(testKWD, value)).shouldBe("KWD1")
    formatter.formatCompact(FiatMoney(USD, value)).shouldBe("$1")
  }

  test("Format standard bitcoin - BTC preference satoshis") {
    val value = 1.toBigDecimal()
    bitcoinDisplayPreferenceRepository.internalBitcoinDisplayUnit.emit(BitcoinDisplayUnit.Satoshi)
    formatter.format(BitcoinMoney.btc(value)).shouldBe("100,000,000 sats")
  }

  test("Format standard bitcoin - BTC preference bitcoin") {
    val value = 1.toBigDecimal()
    bitcoinDisplayPreferenceRepository.internalBitcoinDisplayUnit.emit(BitcoinDisplayUnit.Bitcoin)
    formatter.format(BitcoinMoney.btc(value)).shouldBe("1 BTC")
  }
})
