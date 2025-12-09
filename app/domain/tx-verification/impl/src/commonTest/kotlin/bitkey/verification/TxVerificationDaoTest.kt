package bitkey.verification

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.sqldelight.InMemorySqlDriverFactory
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first

class TxVerificationDaoTest : FunSpec({
  val driverFactory = InMemorySqlDriverFactory()
  val databaseProvider = BitkeyDatabaseProviderImpl(driverFactory)
  val dao = TxVerificationDaoImpl(
    databaseProvider = databaseProvider
  )

  beforeTest {
    dao.deletePolicy()
  }

  test("Active policy's currency not found returns error") {
    val active = VerificationThreshold.Enabled(
      amount = FiatMoney(
        currency = FiatCurrency(
          textCode = IsoCurrencyTextCode("FAKE"),
          unitSymbol = null,
          fractionalDigits = 1,
          displayConfiguration = FiatCurrency.DisplayConfiguration(
            name = "fake",
            displayCountryCode = "NA"
          )
        ),
        value = BigDecimal.ONE
      )
    )

    dao.setEnabledThreshold(active)

    val result = dao.getActivePolicy().first()

    result.isErr.shouldBeTrue()
    result.error.shouldBeInstanceOf<InvalidPolicyError>()
  }
})
