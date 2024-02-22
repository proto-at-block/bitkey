package build.wallet.money.exchange

import app.cash.turbine.test
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class ExchangeRateDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  lateinit var dao: ExchangeRateDaoImpl

  val rate1 = USDtoBTC(0.000041)
  val rate2 = USDtoBTC(0.000042)
  val rate3 =
    ExchangeRate(
      fromCurrency = IsoCurrencyTextCode("EUR"),
      toCurrency = IsoCurrencyTextCode("BTC"),
      rate = 0.000043,
      timeRetrieved = Instant.fromEpochSeconds(1706730439)
    )

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = ExchangeRateDaoImpl(databaseProvider)
  }

  test("store and retrieve rate") {
    dao.allExchangeRates().test {
      awaitItem().shouldBeEmpty()
      dao.storeExchangeRate(rate1)
      awaitItem().shouldBe(listOf(rate1))
    }
  }

  test("store and retrieve rate without time retrieved") {
    dao.allExchangeRates().test {
      val rate1WithoutTime = rate1.copy(timeRetrieved = Instant.fromEpochSeconds(1706730439))
      awaitItem().shouldBeEmpty()
      dao.storeExchangeRate(rate1WithoutTime)
      awaitItem().shouldBe(listOf(rate1WithoutTime))
    }
  }

  test("new rate overrides old rate for same currency pair") {
    dao.allExchangeRates().test {
      awaitItem().shouldBeEmpty()

      dao.storeExchangeRate(rate1)
      awaitItem().shouldBe(listOf(rate1))

      dao.storeExchangeRate(rate2)
      awaitItem().shouldBe(listOf(rate2))
    }
  }

  test("different currency pairs are stored as separate rates") {
    dao.allExchangeRates().test {
      awaitItem().shouldBeEmpty()

      dao.storeExchangeRate(rate1)
      awaitItem().shouldBe(listOf(rate1))

      dao.storeExchangeRate(rate3)
      awaitItem().shouldBe(listOf(rate1, rate3))
    }
  }
})
