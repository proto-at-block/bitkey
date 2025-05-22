package bitkey.verification

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.sqldelight.InMemorySqlDriverFactory
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

class TxVerificationDaoTest : FunSpec({
  val driverFactory = InMemorySqlDriverFactory()
  val databaseProvider = BitkeyDatabaseProviderImpl(driverFactory)
  val clock = ClockFake()
  val dao = TxVerificationDaoImpl(
    databaseProvider = databaseProvider,
    clock = clock
  )

  beforeTest {
    dao.clear()
  }

  test("Latest effective policy is returned") {
    val active = TxVerificationPolicy(
      id = TxVerificationPolicy.Id("active-policy"),
      threshold = VerificationThreshold.Disabled,
      authorization = null
    )
    val inactive = TxVerificationPolicy(
      id = TxVerificationPolicy.Id("inactive-policy"),
      threshold = VerificationThreshold.Disabled,
      authorization = null
    )

    dao.setPolicy(active)
    dao.markPolicyEffective(active.id)
    dao.setPolicy(inactive)

    val result = dao.getEffectivePolicy().first()

    result
      .shouldBeOk()
      .shouldBe(active)
  }

  test("Pending Policies are returned") {
    val active = TxVerificationPolicy(
      id = TxVerificationPolicy.Id("active-policy"),
      threshold = VerificationThreshold.Disabled,
      authorization = null
    )
    val laterPending = TxVerificationPolicy(
      id = TxVerificationPolicy.Id("inactive-policy-1"),
      threshold = VerificationThreshold.Always,
      authorization = TxVerificationPolicy.DelayNotifyAuthorization(
        delayEndTime = Instant.fromEpochSeconds(9999),
        cancellationToken = "cancel-token-1",
        completionToken = "complete-token-1"
      )
    )
    val earlierPending = TxVerificationPolicy(
      id = TxVerificationPolicy.Id("inactive-policy-2"),
      threshold = VerificationThreshold.Always,
      authorization = TxVerificationPolicy.DelayNotifyAuthorization(
        delayEndTime = Instant.fromEpochSeconds(123),
        cancellationToken = "cancel-token-2",
        completionToken = "complete-token-2"
      )
    )

    dao.setPolicy(active)
    dao.setPolicy(laterPending)
    dao.setPolicy(earlierPending)
    dao.markPolicyEffective(active.id)

    val result = dao.getPendingPolicies().first()

    val policies = result.shouldBeOk()
    policies.size.shouldBe(2)

    // Earliest completing policy returns first:
    policies[0].shouldBe(earlierPending)
    policies[1].shouldBe(laterPending)
  }

  test("Active policy's currency not found returns error") {
    val active = TxVerificationPolicy(
      id = TxVerificationPolicy.Id("active-policy"),
      threshold = VerificationThreshold.Enabled(
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
      ),
      authorization = null
    )

    dao.setPolicy(active)
    dao.markPolicyEffective(active.id)

    val result = dao.getEffectivePolicy().first()

    result.isErr.shouldBeTrue()
    result.error.shouldBeInstanceOf<InvalidPolicyError>()
  }

  test("Pending policy's currency not found removes from list") {
    val valid = TxVerificationPolicy(
      id = TxVerificationPolicy.Id("inactive-policy-1"),
      threshold = VerificationThreshold.Always,
      authorization = TxVerificationPolicy.DelayNotifyAuthorization(
        delayEndTime = Instant.fromEpochSeconds(9999),
        cancellationToken = "cancel-token-1",
        completionToken = "complete-token-1"
      )
    )
    val invalid = TxVerificationPolicy(
      id = TxVerificationPolicy.Id("active-policy"),
      threshold = VerificationThreshold.Enabled(
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
      ),
      authorization = null
    )

    dao.setPolicy(valid)
    dao.setPolicy(invalid)

    val result = dao.getPendingPolicies().first()

    result.shouldBeOk().shouldContainOnly(valid)
  }
})
