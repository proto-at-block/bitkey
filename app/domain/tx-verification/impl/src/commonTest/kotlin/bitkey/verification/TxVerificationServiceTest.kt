package bitkey.verification

import bitkey.f8e.verify.TxVerifyPolicyF8eClientFake
import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.BTC
import build.wallet.sqldelight.InMemorySqlDriverFactory
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant

class TxVerificationServiceTest : FunSpec({
  val driverFactory = InMemorySqlDriverFactory()
  val databaseProvider = BitkeyDatabaseProviderImpl(driverFactory)
  val dao = TxVerificationDaoImpl(databaseProvider)
  val accountService = AccountServiceFake()
  val clock = ClockFake()
  val f8eClient = TxVerifyPolicyF8eClientFake(clock)
  val service = TxVerificationServiceImpl(
    txVerificationDao = dao,
    txVerificationF8eClient = f8eClient,
    accountService = accountService
  )

  beforeTest {
    dao.clear()
    accountService.reset()
    f8eClient.reset()
    clock.reset()
  }

  test("Current Threshold - Not set") {
    service.getCurrentThreshold().first().shouldBeOk(VerificationThreshold.Disabled)
  }

  test("Current Threshold - set") {
    val amount = BitcoinMoney(BTC, 1.toBigDecimal())
    dao.setActivePolicy(VerificationThreshold.Enabled(amount)).shouldBeOk()
    service.getCurrentThreshold().first().shouldBeOk(VerificationThreshold.Enabled(amount))
  }

  test("Pending Policy - none set") {
    service.getPendingPolicy().first().shouldBeOk(null)

    // Set a current policy to test if these are accidentally conflated:
    val amount = BitcoinMoney(BTC, 1.toBigDecimal())
    dao.setActivePolicy(VerificationThreshold.Enabled(amount)).shouldBeOk()
    service.getPendingPolicy().first().shouldBeOk(null)
  }

  test("Pending Policy - set") {
    dao.createPendingPolicy(
      threshold = VerificationThreshold.Always,
      auth = TxVerificationPolicy.DelayNotifyAuthorization(
        delayEndTime = Instant.fromEpochSeconds(123),
        id = TxVerificationPolicy.DelayNotifyAuthorization.AuthId("fake-auth-id"),
        cancellationToken = "cancellationToken",
        completionToken = "completionToken"
      )
    )

    service.getPendingPolicy().first()
      .shouldBeOk()
      .shouldNotBeNull()
      .run {
        threshold.shouldBe(VerificationThreshold.Always)
        authorization.delayEndTime.shouldBe(Instant.fromEpochSeconds(123))
        authorization.completionToken.shouldBe("completionToken")
        authorization.cancellationToken.shouldBe("cancellationToken")
        authorization.id.value.shouldBe("fake-auth-id")
      }
  }

  test("Pending Policy - multiple set") {
    dao.createPendingPolicy(
      threshold = VerificationThreshold.Always,
      auth = TxVerificationPolicy.DelayNotifyAuthorization(
        delayEndTime = Instant.fromEpochSeconds(456),
        id = TxVerificationPolicy.DelayNotifyAuthorization.AuthId("second-auth-id"),
        cancellationToken = "cancellationToken",
        completionToken = "completionToken"
      )
    )
    dao.createPendingPolicy(
      threshold = VerificationThreshold.Always,
      auth = TxVerificationPolicy.DelayNotifyAuthorization(
        delayEndTime = Instant.fromEpochSeconds(123),
        id = TxVerificationPolicy.DelayNotifyAuthorization.AuthId("first-auth-id"),
        cancellationToken = "cancellationToken",
        completionToken = "completionToken"
      )
    )

    service.getPendingPolicy().first()
      .shouldBeOk()
      .shouldNotBeNull()
      .run {
        threshold.shouldBe(VerificationThreshold.Always)
        authorization.delayEndTime.shouldBe(Instant.fromEpochSeconds(123))
        authorization.id.value.shouldBe("first-auth-id")
      }
  }

  test("Update Policy - no auth") {
    accountService.setActiveAccount(FullAccountMock)
    service.updateThreshold(VerificationThreshold.Always)
      .shouldBeOk {
        it.shouldBeInstanceOf<TxVerificationPolicy.Active>()
      }
  }

  test("Update Policy - delay notify") {
    accountService.setActiveAccount(FullAccountMock)
    service.updateThreshold(VerificationThreshold.Always).shouldBeOk()
    service.updateThreshold(VerificationThreshold.Disabled).shouldBeOk {
      it.shouldBeInstanceOf<TxVerificationPolicy.Pending>()
    }
  }
})
