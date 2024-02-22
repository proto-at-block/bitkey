package build.wallet.limit

import app.cash.turbine.test
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.database.sqldelight.FiatCurrencyQueries
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.USD
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.AmericaLosAngeles
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.TimeZone

class SpendingLimitDaoImplTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()

  val limit1 =
    SpendingLimit(
      active = true,
      amount = FiatMoney.usd(100.0),
      timezone = TimeZone.AmericaLosAngeles
    )

  lateinit var dao: SpendingLimitDao

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    dao = SpendingLimitDaoImpl(databaseProvider)

    // Add USD to the fiat currency db table to have available
    databaseProvider.database().fiatCurrencyQueries.insertOrUpdateFiatCurrency(USD)
  }

  test("Active limit flow") {

    dao.activeSpendingLimit().test {
      awaitItem().shouldBeNull()
      dao.saveAndSetSpendingLimit(limit = limit1)
      awaitItem().shouldBe(limit1)
      dao.disableSpendingLimit()
      awaitItem().shouldBeNull()
    }
  }

  test("Get, save and clear active spending limit") {
    dao.getActiveSpendingLimit().shouldBe(Ok(null))

    dao.saveAndSetSpendingLimit(
      limit = limit1
    )

    dao.getActiveSpendingLimit().shouldBe(Ok(limit1))

    dao.disableSpendingLimit()
    dao.getActiveSpendingLimit().shouldBe(Ok(null))

    val limit2 =
      SpendingLimit(
        active = true,
        amount = FiatMoney.usd(200.0),
        timezone = TimeZone.AmericaLosAngeles
      )
    dao.saveAndSetSpendingLimit(
      limit = limit2
    )

    dao.getActiveSpendingLimit().shouldBe(
      Ok(limit2)
    )
  }

  test("Get most recent spending limit") {
    dao.mostRecentSpendingLimit().shouldBe(Ok(null))

    dao.saveAndSetSpendingLimit(
      limit = limit1
    )

    dao.mostRecentSpendingLimit().shouldBe(
      Ok(limit1)
    )

    dao.disableSpendingLimit()
    // We should still return the most recent limit, even if it's not active.
    dao.mostRecentSpendingLimit().shouldBe(
      Ok(limit1.copy(active = false))
    )

    val limit2 =
      SpendingLimit(
        active = true,
        amount = FiatMoney.usd(200.0),
        timezone = TimeZone.AmericaLosAngeles
      )
    dao.saveAndSetSpendingLimit(
      limit = limit2
    )

    dao.mostRecentSpendingLimit().shouldBe(
      Ok(limit2)
    )
  }
})

private fun FiatCurrencyQueries.insertOrUpdateFiatCurrency(fiatCurrency: FiatCurrency) {
  insertOrUpdateFiatCurrency(
    textCode = fiatCurrency.textCode,
    fractionalDigits = fiatCurrency.fractionalDigits.toLong(),
    displayUnitSymbol = fiatCurrency.unitSymbol,
    displayName = fiatCurrency.displayConfiguration.name,
    displayCountryCode = fiatCurrency.displayConfiguration.displayCountryCode
  )
}
