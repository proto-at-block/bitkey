package bitkey.verification

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.sqldelight.InMemorySqlDriverFactory
import build.wallet.testing.shouldBeOk
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

class TxVerificationDaoTest : FunSpec({
  val driverFactory = InMemorySqlDriverFactory()
  val databaseProvider = BitkeyDatabaseProviderImpl(driverFactory)
  val dao = TxVerificationDaoImpl(
    databaseProvider = databaseProvider
  )

  beforeTest {
    dao.clear()
  }

  test("Latest effective policy is returned") {
    val active = VerificationThreshold.Enabled(BitcoinMoney.sats(123))
    val inactive = VerificationThreshold.Disabled

    dao.setActivePolicy(active)
    dao.createPendingPolicy(inactive, TxVerificationPolicyAuthFake)

    val result = dao.getActivePolicy().first().shouldBeOk()

    result?.threshold.shouldBe(active)
  }

  test("Pending Policies are returned") {
    val active = VerificationThreshold.Always
    val laterPendingThreshold = VerificationThreshold.Enabled(BitcoinMoney.sats(123))
    val laterPendingAuth = TxVerificationPolicyAuthFake.copy(
      id = TxVerificationPolicy.DelayNotifyAuthorization.AuthId("fake-auth-id-1"),
      delayEndTime = Instant.fromEpochSeconds(9999)
    )
    val earlierPendingThreshold = VerificationThreshold.Enabled(BitcoinMoney.sats(456))
    val earlierPendingAuth = TxVerificationPolicyAuthFake.copy(
      id = TxVerificationPolicy.DelayNotifyAuthorization.AuthId("fake-auth-id-2"),
      delayEndTime = Instant.fromEpochSeconds(123)
    )

    dao.setActivePolicy(active)
    dao.createPendingPolicy(laterPendingThreshold, laterPendingAuth)
    dao.createPendingPolicy(earlierPendingThreshold, earlierPendingAuth)

    val result = dao.getPendingPolicies().first()

    val policies = result.shouldBeOk()
    policies.size.shouldBe(2)

    // Earliest completing policy returns first:
    policies[0].threshold.shouldBeEqual(earlierPendingThreshold)
    policies[0].authorization.shouldBe(earlierPendingAuth)
    policies[1].threshold.shouldBeEqual(laterPendingThreshold)
    policies[1].authorization.shouldBe(laterPendingAuth)
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

    dao.setActivePolicy(active)

    val result = dao.getActivePolicy().first()

    result.isErr.shouldBeTrue()
    result.error.shouldBeInstanceOf<InvalidPolicyError>()
  }

  test("Pending policy's currency not found removes from list") {
    val valid = VerificationThreshold.Always
    val invalid = VerificationThreshold.Enabled(
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

    dao.createPendingPolicy(valid, TxVerificationPolicyAuthFake)
    dao.createPendingPolicy(invalid, TxVerificationPolicyAuthFake)

    val result = dao.getPendingPolicies().first().shouldBeOk()

    result[0].authorization.shouldBe(TxVerificationPolicyAuthFake)
    result[0].threshold.shouldBeEqual(valid)
  }
})
