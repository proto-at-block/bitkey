package build.wallet.money.exchange

import app.cash.turbine.test
import build.wallet.account.AccountRepositoryFake
import build.wallet.account.AccountStatus
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.currency.USD
import build.wallet.money.testKWD
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class CurrencyConverterImplTests : FunSpec({
  val exchangeRateDao = ExchangeRateDaoFake()
  val accountRepository = AccountRepositoryFake()
  val exchangeRateF8eClient = ExchangeRateF8eClientMock()
  val converter = CurrencyConverterImpl(
    accountRepository = accountRepository,
    exchangeRateDao = exchangeRateDao,
    exchangeRateF8eClient = exchangeRateF8eClient
  )

  beforeTest {
    accountRepository.reset()
    exchangeRateDao.reset()
    exchangeRateF8eClient.reset()
  }

  test("Convert zero amount from USD to BTC") {
    converter.convert(FiatMoney.zeroUsd(), BTC, listOf(USDtoBTC(0.5)))
      .shouldNotBeNull()
      .shouldBe(BitcoinMoney.zero())
  }

  test("Convert positive amount from USD to BTC") {
    converter.convert(FiatMoney.usd(dollars = 10.0), BTC, listOf(USDtoBTC(0.5)))
      .shouldNotBeNull()
      .shouldBe(BitcoinMoney.btc(5.0))
  }

  test("Convert negative amount from USD to BTC") {
    converter.convert(FiatMoney.usd(-10.0), BTC, listOf(USDtoBTC(0.5)))
      .shouldNotBeNull()
      .shouldBe(BitcoinMoney.btc(-5.0))
  }

  test("Convert zero amount from BTC to USD (inverse conversion)") {
    converter.convert(BitcoinMoney.zero(), USD, listOf(USDtoBTC(0.5)))
      .shouldNotBeNull()
      .shouldBe(FiatMoney.zeroUsd())
  }

  test("Convert positive amount from BTC to USD (inverse conversion)") {
    converter.convert(BitcoinMoney.btc(5.toBigDecimal()), USD, listOf(USDtoBTC(0.5)))
      .shouldNotBeNull()
      .shouldBe(FiatMoney.usd(10.0))
  }

  test("Convert positive amount from BTC to USD (inverse conversion) with rounding") {
    converter.convert(BitcoinMoney.btc(5.55555.toBigDecimal()), USD, listOf(USDtoBTC(0.5)))
      .shouldNotBeNull()
      .shouldBe(FiatMoney.usd(11.11.toBigDecimal()))
  }

  test("Convert negative amount from BTC to USD (inverse conversion)") {
    converter.convert(BitcoinMoney.btc((-5).toBigDecimal()), USD, listOf(USDtoBTC(0.5)))
      .shouldNotBeNull()
      .shouldBe(FiatMoney.usd((-10).toBigDecimal()))
  }

  test("Convert negative amount from BTC to USD (inverse conversion) with rounding") {
    converter.convert(BitcoinMoney.btc((-5.55555).toBigDecimal()), USD, listOf(USDtoBTC(0.5)))
      .shouldNotBeNull()
      .shouldBe(FiatMoney.usd((-11.11).toBigDecimal()))
  }

  test("Convert zero amount from USD to currency with an unknown rate") {
    converter.convert(FiatMoney.zeroUsd(), testKWD, listOf(USDtoBTC(0.5)))
      .shouldBeNull()
  }

  test("Convert negative amount from USD to currency with an unknown rate") {
    converter.convert(FiatMoney.usd((-5).toBigDecimal()), testKWD, listOf(USDtoBTC(0.5)))
      .shouldBeNull()
  }

  test("Convert positive amount from USD to currency with an unknown rate") {
    converter.convert(FiatMoney.usd(5.toBigDecimal()), testKWD, listOf(USDtoBTC(0.5)))
      .shouldBeNull()
  }

  test("Convert historical amount from BTC to USD, time is null") {
    exchangeRateDao.historicalExchangeRates.value = emptyMap()
    exchangeRateDao.allExchangeRates.value = listOf(USDtoBTC(0.5))

    // Should have the same behavior as getting the current rate if time is null
    converter.convert(BitcoinMoney.btc(1.toBigDecimal()), USD, null).test {
      awaitItem().shouldBe(FiatMoney.usd(2.toBigDecimal()))
    }
  }

  test("Convert historical amount from BTC to USD, with only USD to BTC rate in DB") {
    val time = Instant.fromEpochMilliseconds(1673338945736L)
    exchangeRateDao.historicalExchangeRates.value = mapOf(time to listOf(USDtoBTC(0.5)))

    converter.convert(BitcoinMoney.btc(1.toBigDecimal()), USD, time).test {
      awaitItem().shouldBe(FiatMoney.usd(2.toBigDecimal()))
      awaitComplete()
    }
  }

  test("Convert historical amount from BTC to USD, historical rate not in DB, F8e success") {
    val time = Instant.fromEpochMilliseconds(1673338945736L)
    val differentTime = Instant.fromEpochMilliseconds(1773338945736L)
    exchangeRateDao.allExchangeRates.value = listOf(USDtoBTC(0.1))
    exchangeRateDao.historicalExchangeRates.value = mapOf(differentTime to listOf(USDtoBTC(0.2)))
    accountRepository.accountState.value = Ok(AccountStatus.ActiveAccount(FullAccountMock))

    exchangeRateF8eClient.historicalBtcToUsdExchangeRate.value = Ok(USDtoBTC(0.5))

    // The value for `historicalExchangeRates` doesn't match the time, so we should use
    // the value from F8e
    converter.convert(BitcoinMoney.btc(1.toBigDecimal()), USD, time).test {
      // Uses the `allExchangeRates` value while the value from bitstamp is loading
      awaitItem().shouldBe(FiatMoney.usd(10.toBigDecimal()))
      // Value from F8e should then be used and stored
      awaitItem().shouldBe(FiatMoney.usd(2.toBigDecimal()))
      exchangeRateDao.historicalExchangeRates.test {
        awaitItem().shouldBe(
          mapOf(
            differentTime to listOf(USDtoBTC(0.2)),
            time to listOf(USDtoBTC(0.5))
          )
        )
      }
      awaitComplete()
    }
  }

  test("Convert historical amount from BTC to USD, historical rate not in DB, F8e failure") {
    val time = Instant.fromEpochMilliseconds(1673338945736L)
    val differentTime = Instant.fromEpochMilliseconds(1773338945736L)
    exchangeRateDao.allExchangeRates.value = listOf(USDtoBTC(0.1))
    exchangeRateDao.storeHistoricalExchangeRate(USDtoBTC(0.5), differentTime)
    exchangeRateF8eClient.historicalBtcToUsdExchangeRate.value =
      Err(NetworkError(Throwable()))

    // Should have the same behavior as getting the current rate if we failed to look up in db
    // or on bitstamp – so should look in the db for `allExchangeRates` and then also listen
    // to update
    converter.convert(BitcoinMoney.btc(1.toBigDecimal()), USD, time).test {
      awaitItem().shouldBe(FiatMoney.usd(10.toBigDecimal()))
      exchangeRateDao.allExchangeRates.value = listOf(USDtoBTC(2.0))
      awaitItem().shouldBe(FiatMoney.usd(0.5.toBigDecimal()))
    }
  }

  test(
    "Convert historical amount from BTC to USD, historical and current rate not in DB, F8e failure"
  ) {
    val time = Instant.fromEpochMilliseconds(1673338945736L)
    val differentTime = Instant.fromEpochMilliseconds(1773338945736L)
    exchangeRateDao.historicalExchangeRates.value = mapOf(differentTime to listOf(USDtoBTC(0.5)))
    exchangeRateF8eClient.historicalBtcToUsdExchangeRate.value =
      Err(NetworkError(Throwable()))

    converter.convert(BitcoinMoney.btc(1.toBigDecimal()), USD, time).test {
      awaitItem().shouldBeNull()
    }
  }
})
