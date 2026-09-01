package build.wallet.money.formatter

import build.wallet.amount.DoubleFormatterImpl
import build.wallet.money.*
import build.wallet.money.currency.EUR
import build.wallet.money.currency.GBP
import build.wallet.money.currency.USD
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryMock
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.platform.settings.LocaleProviderFake
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MoneyDisplayFormatterImplTests : FunSpec({

  val bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryMock()
  val localeProvider = LocaleProviderFake()
  val formatter = MoneyDisplayFormatterImpl(
    bitcoinDisplayPreferenceRepository = bitcoinDisplayPreferenceRepository,
    moneyFormatterDefinitions = MoneyFormatterDefinitionsImpl(
      doubleFormatter = DoubleFormatterImpl(localeProvider)
    )
  )

  beforeTest {
    bitcoinDisplayPreferenceRepository.reset()
  }

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

  test("Format standard bitcoin - BTC preference bitcoin") {
    val value = 1.toBigDecimal()
    bitcoinDisplayPreferenceRepository.internalBitcoinDisplayUnit.emit(BitcoinDisplayUnit.Bitcoin)
    formatter.format(BitcoinMoney.btc(value)).shouldBe("1 BTC")
  }

  test("Format satoshis uses symbol prefix") {
    val value = 1.toBigDecimal()
    bitcoinDisplayPreferenceRepository.internalBitcoinDisplayUnit.emit(BitcoinDisplayUnit.Satoshi)
    formatter.format(BitcoinMoney.btc(value)).shouldBe("₿100,000,000")
  }

  test("amountDisplayText with null fiat returns btc primary and null secondary") {
    bitcoinDisplayPreferenceRepository.internalBitcoinDisplayUnit.emit(BitcoinDisplayUnit.Bitcoin)
    val amountDisplayText = formatter.amountDisplayText(
      bitcoinAmount = BitcoinMoney.btc(1.0),
      fiatAmount = null,
      withPendingFormat = false
    )

    val expected = AmountDisplayText(
      primaryAmountText = "1 BTC",
      secondaryAmountText = null
    )
    amountDisplayText.shouldBe(expected)
  }

  test("amountDisplayText with nonnull fiat returns fiat primary and btc secondary") {
    bitcoinDisplayPreferenceRepository.internalBitcoinDisplayUnit.emit(BitcoinDisplayUnit.Bitcoin)
    val amountDisplayText = formatter.amountDisplayText(
      bitcoinAmount = BitcoinMoney.btc(1.0),
      fiatAmount = FiatMoney.usd(1.0),
      withPendingFormat = false
    )

    val expected = AmountDisplayText(
      primaryAmountText = "$1.00",
      secondaryAmountText = "1 BTC"
    )
    amountDisplayText.shouldBe(expected)
  }

  test("amountDisplayText with withPendingFormat equal to true prepends ~ to fiat") {
    bitcoinDisplayPreferenceRepository.internalBitcoinDisplayUnit.emit(BitcoinDisplayUnit.Bitcoin)
    val amountDisplayText = formatter.amountDisplayText(
      bitcoinAmount = BitcoinMoney.btc(1.0),
      fiatAmount = FiatMoney.usd(1.0),
      withPendingFormat = true
    )

    val expected = AmountDisplayText(
      primaryAmountText = "~$1.00",
      secondaryAmountText = "1 BTC"
    )
    amountDisplayText.shouldBe(expected)
  }

  test("formatWithUnit formats as BTC regardless of user preference") {
    // User prefers satoshis, but we explicitly request BTC format
    bitcoinDisplayPreferenceRepository.internalBitcoinDisplayUnit.emit(BitcoinDisplayUnit.Satoshi)

    formatter.formatWithUnit(BitcoinMoney.btc(1.0), BitcoinDisplayUnit.Bitcoin)
      .shouldBe("1 BTC")
  }

  test("formatWithUnit formats with BIP 177 symbol") {
    // User prefers BTC, but we explicitly request satoshi format
    bitcoinDisplayPreferenceRepository.internalBitcoinDisplayUnit.emit(BitcoinDisplayUnit.Bitcoin)

    formatter.formatWithUnit(BitcoinMoney.btc(1.0), BitcoinDisplayUnit.Satoshi)
      .shouldBe("₿100,000,000")
  }
})
