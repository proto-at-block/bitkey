@file:OptIn(
  ExperimentalStdlibApi::class, ExperimentalCoroutinesApi::class,
  DelicateCoroutinesApi::class
)

package build.wallet.money.exchange

import app.cash.turbine.test
import bitkey.account.AccountConfigServiceFake
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.account.AccountStatus.NoAccount
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keybox.SoftwareAccountMock
import build.wallet.ktor.result.HttpError.UnhandledException
import build.wallet.money.currency.USD
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class ExchangeRateServiceImplTests : FunSpec({
  val exchangeRateDao = ExchangeRateDaoFake()
  val exchangeRateF8eClient = ExchangeRateF8eClientFake()
  val appSessionManager = AppSessionManagerFake()
  val accountService = AccountServiceFake()
  val accountConfigService = AccountConfigServiceFake()
  val testScope = TestScope()

  lateinit var exchangeRateService: ExchangeRateServiceImpl

  val exchangeRate1 = USDtoBTC(0.5)
  val eurtoBtcExchangeRate = EURtoBTC(0.7)
  val syncFrequency = 100.milliseconds

  beforeTest {
    appSessionManager.reset()
    exchangeRateDao.reset()
    exchangeRateF8eClient.apply {
      exchangeRates.value = Ok(emptyList())
    }
    accountService.apply {
      reset()
      accountState.value = Ok(ActiveAccount(FullAccountMock))
    }
    accountConfigService.reset()

    exchangeRateService = ExchangeRateServiceImpl(
      exchangeRateDao = exchangeRateDao,
      exchangeRateF8eClient = exchangeRateF8eClient,
      accountService = accountService,
      accountConfigService = accountConfigService,
      clock = ClockFake(now = Instant.fromEpochSeconds(500)),
      exchangeRateSyncFrequency = ExchangeRateSyncFrequency(syncFrequency),
      appScope = testScope
    )
  }

  test("default value is empty rates") {
    exchangeRateService.exchangeRates.test {
      awaitItem().shouldBeEmpty()
    }
  }

  test("worker updates exchange rate") {
    exchangeRateF8eClient.exchangeRates.value = Ok(listOf(exchangeRate1))

    exchangeRateService.exchangeRates.value.shouldBeEmpty()

    exchangeRateService.executeWork()
    testScope.runCurrent()

    exchangeRateService.exchangeRates.value.shouldBe(listOf(exchangeRate1))
  }

  test("ignores sync failure") {
    // f8e returns error
    exchangeRateF8eClient.exchangeRates.value = Err(UnhandledException(Exception("oops")))
    exchangeRateService.executeWork()
    testScope.runCurrent()

    // no rates updated
    exchangeRateService.exchangeRates.value.shouldBeEmpty()

    // f8e returns ok
    exchangeRateF8eClient.exchangeRates.value = Ok(listOf(exchangeRate1))
    exchangeRateService.executeWork()
    testScope.runCurrent()

    // rates updated
    exchangeRateService.exchangeRates.value.shouldContainExactly(exchangeRate1)
    exchangeRateDao.allExchangeRates.value.shouldContainExactly(exchangeRate1)
  }

  test("sync multiple currencies") {
    val exchangeRates = listOf(exchangeRate1, eurtoBtcExchangeRate)
    exchangeRateF8eClient.exchangeRates.value = Ok(exchangeRates)
    exchangeRateService.executeWork()
    testScope.runCurrent()
    exchangeRateDao.allExchangeRates.value.shouldContainExactly(exchangeRates)
  }

  test("retrieving existing exchange rates in past 10 mins") {
    val exchangeRates = listOf(exchangeRate1, eurtoBtcExchangeRate)
    exchangeRateF8eClient.exchangeRates.value = Ok(exchangeRates)

    exchangeRateService.executeWork()
    testScope.runCurrent()

    exchangeRateService.mostRecentRatesSinceDurationForCurrency(10.minutes, USD)
      .shouldNotBeNull()
      .shouldContainExactly(exchangeRates)
  }

  test("retrieving existing exchange rates in past 5 mins") {
    val exchangeRates = listOf(exchangeRate1, eurtoBtcExchangeRate)
    exchangeRateF8eClient.exchangeRates.value = Ok(exchangeRates)

    exchangeRateService.executeWork()
    testScope.runCurrent()

    exchangeRateService.mostRecentRatesSinceDurationForCurrency(5.minutes, USD)
      .shouldBeNull()
  }

  test("syncs exchange rates for software accounts") {
    accountService.accountState.value = Ok(ActiveAccount(SoftwareAccountMock))
    exchangeRateF8eClient.exchangeRates.value = Ok(listOf(exchangeRate1))

    exchangeRateService.executeWork()
    testScope.runCurrent()

    exchangeRateService.exchangeRates.value.shouldContainExactly(exchangeRate1)
  }

  test("does not sync exchange rates for lite accounts") {
    accountService.accountState.value = Ok(ActiveAccount(LiteAccountMock))
    exchangeRateF8eClient.exchangeRates.value = Ok(listOf(exchangeRate1))

    exchangeRateService.executeWork()
    testScope.runCurrent()

    exchangeRateService.exchangeRates.value.shouldBeEmpty()
  }

  test("syncs exchange rates during recovery when no account is active") {
    accountService.accountState.value = Ok(NoAccount)
    exchangeRateF8eClient.exchangeRates.value = Ok(listOf(exchangeRate1))

    exchangeRateService.executeWork()
    testScope.runCurrent()

    exchangeRateService.exchangeRates.value.shouldContainExactly(exchangeRate1)
    exchangeRateDao.allExchangeRates.value.shouldContainExactly(exchangeRate1)
  }

  test("prefers active account F8e environment over recovery default") {
    accountService.accountState.value = Ok(ActiveAccount(FullAccountMock))
    exchangeRateF8eClient.exchangeRates.value = Ok(listOf(exchangeRate1))

    exchangeRateService.executeWork()
    testScope.runCurrent()

    exchangeRateService.exchangeRates.value.shouldContainExactly(exchangeRate1)
  }
})
