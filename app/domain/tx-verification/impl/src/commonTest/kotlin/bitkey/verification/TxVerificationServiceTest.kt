@file:OptIn(ExperimentalCoroutinesApi::class)

package bitkey.verification

import app.cash.turbine.test
import bitkey.f8e.verify.TxVerificationF8eClientFake
import bitkey.f8e.verify.TxVerifyPolicyF8eClientFake
import bitkey.privilegedactions.AuthorizationStrategyType
import bitkey.privilegedactions.PrivilegedActionType
import bitkey.verification.TxVerificationPolicy.Active
import build.wallet.account.AccountServiceFake
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.BTC
import build.wallet.money.currency.USD
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryFake
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryFake
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.exchange.ExchangeRateFake
import build.wallet.money.exchange.USDtoBTC
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds

class TxVerificationServiceTest : FunSpec({
  val dao = TxVerificationDaoFake()
  val accountService = AccountServiceFake()
  val clock = ClockFake()
  val f8eClient = TxVerifyPolicyF8eClientFake()
  val currencyConverter = CurrencyConverterFake()
  val verificationClient = TxVerificationF8eClientFake()
  val bitkeyDatabaseProvider = BitkeyDatabaseProviderImpl(inMemorySqlDriver().factory, TestScope())
  val service = TxVerificationServiceImpl(
    txVerificationDao = dao,
    policyClient = f8eClient,
    verificationClient = verificationClient,
    accountService = accountService,
    currencyConverter = currencyConverter,
    bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryFake(),
    fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryFake(),
    bitkeyDatabaseProvider = bitkeyDatabaseProvider
  )

  beforeTest {
    dao.deletePolicy()
    accountService.reset()
    f8eClient.reset()
    clock.reset()
  }

  test("Current Threshold - Not set") {
    service.getCurrentThreshold().first().value.shouldBeNull()
  }

  test("Current Threshold - set") {
    val threshold = VerificationThreshold(BitcoinMoney(BTC, 1.toBigDecimal()))
    dao.setActivePolicy(txVerificationPolicy = Active(threshold)).shouldBeOk()
    service.getCurrentThreshold().first().shouldBeOk(threshold)
  }

  test("Pending Policy - none set") {
    service.getPendingPolicy().first().shouldBeOk(null)

    // Set a current policy to test if these are accidentally conflated:
    val amount = BitcoinMoney(BTC, 1.toBigDecimal())
    dao.setActivePolicy(Active(VerificationThreshold(amount))).shouldBeOk()
    service.getPendingPolicy().first().shouldBeOk(null)
  }

  test("Pending Policy - set") {
    bitkeyDatabaseProvider.database()
      .pendingPrivilegedActionsQueries
      .insertPendingAction(
        "id",
        PrivilegedActionType.LOOSEN_TRANSACTION_VERIFICATION_POLICY,
        AuthorizationStrategyType.OUT_OF_BAND
      )

    service.getPendingPolicy().first()
      .shouldBeOk()
      .shouldNotBeNull()
      .run {
        authorization.id.shouldBe("id")
        authorization.privilegedActionType.shouldBe(PrivilegedActionType.LOOSEN_TRANSACTION_VERIFICATION_POLICY)
        authorization.authorizationStrategy.authorizationStrategyType.shouldBe(AuthorizationStrategyType.OUT_OF_BAND)
      }
  }

  test("Update Policy - no auth") {
    accountService.setActiveAccount(FullAccountMock)
    service.updateThreshold(Active(VerificationThreshold.Always), HwFactorProofOfPossession("fake"))
      .shouldBeOk()
  }

  test("Verification Not Enabled") {
    val result = service.isVerificationRequired(
      amount = BitcoinMoney.btc(1.0),
      exchangeRates = null
    )

    result.shouldBeFalse()
  }

  test("Verification Not Required - Fiat threshold") {
    val amount = FiatMoney(USD, 10.0.toBigDecimal())
    dao.setActivePolicy(Active(VerificationThreshold(amount))).shouldBeOk()

    val result = service.isVerificationRequired(
      amount = BitcoinMoney.btc(1.0),
      exchangeRates = listOf(
        USDtoBTC(.5)
      )
    )

    result.shouldBeFalse()
  }

  test("Verification Required - Fiat threshold") {
    val amount = FiatMoney(USD, 10.0.toBigDecimal())
    dao.setActivePolicy(Active(VerificationThreshold(amount))).shouldBeOk()
    currencyConverter.conversionRate = 0.5

    val result = service.isVerificationRequired(
      amount = BitcoinMoney.btc(6.0),
      exchangeRates = listOf(ExchangeRateFake)
    )

    result.shouldBeTrue()
  }

  test("Verification Always Required - always policy") {
    dao.setActivePolicy(Active(VerificationThreshold.Always)).shouldBeOk()

    val result = service.isVerificationRequired(
      amount = BitcoinMoney.sats(1),
      exchangeRates = null
    )

    result.shouldBeTrue()
  }

  test("Verification Required - Bitcoin threshold") {
    val amount = BitcoinMoney.btc(1.0)
    dao.setActivePolicy(Active(VerificationThreshold(amount))).shouldBeOk()

    val result = service.isVerificationRequired(
      amount = BitcoinMoney.btc(1.5),
      exchangeRates = null
    )

    result.shouldBeTrue()
  }

  test("Verification Not Required - Bitcoin threshold") {
    val amount = BitcoinMoney.btc(2.0)
    dao.setActivePolicy(Active(VerificationThreshold(amount))).shouldBeOk()

    val result = service.isVerificationRequired(
      amount = BitcoinMoney.btc(1.5),
      exchangeRates = null
    )

    result.shouldBeFalse()
  }

  test("Verification Required - Bitcoin threshold in sats") {
    val amount = BitcoinMoney.sats(100_000)
    dao.setActivePolicy(Active(VerificationThreshold(amount))).shouldBeOk()

    val result = service.isVerificationRequired(
      amount = BitcoinMoney.sats(150_000),
      exchangeRates = null
    )

    result.shouldBeTrue()
  }

  test("Verification at exact threshold - Bitcoin") {
    val amount = BitcoinMoney.btc(1.0)
    dao.setActivePolicy(Active(VerificationThreshold(amount))).shouldBeOk()

    val result = service.isVerificationRequired(
      amount = BitcoinMoney.btc(1.0),
      exchangeRates = null
    )

    // Assuming exact amount triggers verification
    result.shouldBeTrue()
  }

  test("Verification Skipped - Fiat threshold with no exchange rate") {
    val amount = FiatMoney(USD, 10.0.toBigDecimal())
    dao.setActivePolicy(Active(VerificationThreshold(amount))).shouldBeOk()
    currencyConverter.conversionRate = 0.5

    val result = service.isVerificationRequired(
      amount = BitcoinMoney.btc(6.0),
      exchangeRates = null
    )

    result.shouldBeFalse()
  }

  test("Request Verification") {
    runTest {
      val psbt = Psbt(
        id = "psbt-id",
        base64 = "some-base-64",
        fee = BitcoinMoney.sats(10_000),
        baseSize = 20_000,
        numOfInputs = 1,
        amountSats = 20_000UL
      )

      accountService.setActiveAccount(FullAccountMock)
      val flow = service.requestVerification(psbt).shouldBeOk()

      flow.test {
        awaitItem().shouldBe(ConfirmationState.Pending)
        verificationClient.status = TxVerificationState.Success(FakeTxVerificationApproval)
        advanceTimeBy(5.seconds)
        awaitItem().shouldBe(ConfirmationState.Confirmed(FakeTxVerificationApproval))

        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  test("Request Grant") {
    val psbt = Psbt(
      id = "psbt-id",
      base64 = "some-base-64",
      fee = BitcoinMoney.sats(10_000),
      baseSize = 20_000,
      numOfInputs = 1,
      amountSats = 20_000UL
    )

    accountService.setActiveAccount(FullAccountMock)
    service.requestGrant(psbt).shouldBeOk()
  }
})
