@file:OptIn(
  ExperimentalStdlibApi::class, ExperimentalCoroutinesApi::class,
  DelicateCoroutinesApi::class
)

package build.wallet.money.exchange

import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.keybox.SoftwareAccountMock
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.awaitUntil
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class ExchangeRateServiceImplTests : FunSpec({
  val exchangeRateDao = ExchangeRateDaoFake()
  val exchangeRateF8eClient = ExchangeRateF8eClientFake()
  val appSessionManager = AppSessionManagerFake()
  val accountService = AccountServiceFake()

  lateinit var exchangeRateService: ExchangeRateServiceImpl

  val exchangeRate1 = USDtoBTC(0.5)
  val exchangeRate2 = USDtoBTC(1.0)
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

    exchangeRateService = ExchangeRateServiceImpl(
      exchangeRateDao = exchangeRateDao,
      exchangeRateF8eClient = exchangeRateF8eClient,
      appSessionManager = appSessionManager,
      accountService = accountService,
      clock = ClockFake(now = Instant.fromEpochSeconds(500)),
      exchangeRateSyncFrequency = ExchangeRateSyncFrequency(syncFrequency)
    )
  }

  test("default value is empty rates") {
    exchangeRateService.exchangeRates.test {
      awaitItem().shouldBeEmpty()
    }
  }

  test("sync immediately") {
    createBackgroundScope().launch {
      exchangeRateService.executeWork()
    }

    exchangeRateService.exchangeRates.test {
      awaitItem().shouldBeEmpty()

      exchangeRateF8eClient.exchangeRates.value = Ok(listOf(exchangeRate1))
      awaitItem().shouldBe(listOf(exchangeRate1))
    }

    exchangeRateDao.allExchangeRates.test {
      awaitItem().shouldBe(listOf(exchangeRate1))
    }
  }

  test("ignore sync failure") {
    createBackgroundScope().launch {
      exchangeRateService.executeWork()
    }

    exchangeRateService.exchangeRates.test {
      awaitItem().shouldBeEmpty() // Initial value

      // f8e returns error
      exchangeRateF8eClient.exchangeRates.value = Err(UnhandledException(Exception("oops")))

      // no rates updated
      expectNoEvents()
      exchangeRateDao.allExchangeRates.value.shouldBeEmpty()

      // f8e returns ok
      exchangeRateF8eClient.exchangeRates.value = Ok(listOf(exchangeRate1))

      // rates updated
      awaitItem().shouldContainExactly(exchangeRate1)
      // database updated
      exchangeRateDao.allExchangeRates.value.shouldContainExactly(exchangeRate1)
    }
  }

  test("syncs periodically") {
    createBackgroundScope().launch {
      exchangeRateService.executeWork()
    }

    exchangeRateService.exchangeRates.test {
      awaitItem().shouldBeEmpty() // Initial value

      exchangeRateF8eClient.exchangeRates.value = Ok(listOf(exchangeRate1))

      // rates updated
      awaitItem().shouldContainExactly(exchangeRate1)

      // Update the exchange rate response.
      exchangeRateF8eClient.exchangeRates.value = Ok(listOf(exchangeRate2))

      // new rates updated
      awaitItem().shouldContainExactly(exchangeRate2)
      // database updated
      exchangeRateDao.allExchangeRates.value.shouldContainExactly(exchangeRate2)

      // Update the exchange rate response.
      exchangeRateF8eClient.exchangeRates.value = Ok(listOf(exchangeRate1))

      // new rate updated
      awaitItem().shouldContainExactly(exchangeRate1)
      // database updated
      exchangeRateDao.allExchangeRates.value.shouldContainExactly(exchangeRate1)
    }
  }

  test("sync multiple currencies immediately") {
    val exchangeRates = listOf(exchangeRate1, eurtoBtcExchangeRate)
    exchangeRateF8eClient.exchangeRates.value = Ok(exchangeRates)

    createBackgroundScope().launch {
      exchangeRateService.executeWork()
    }

    exchangeRateService.exchangeRates.test {
      awaitUntil(exchangeRates)
      // database updated
      exchangeRateDao.allExchangeRates.value.shouldContainExactly(exchangeRates)
    }
  }

  test("sync does not occur while app is backgrounded and resumes once foregrounded") {
    appSessionManager.appDidEnterBackground()

    createBackgroundScope().launch {
      exchangeRateService.executeWork()
    }

    exchangeRateService.exchangeRates.test {
      awaitItem().shouldBeEmpty() // Initial value

      val exchangeRates = listOf(exchangeRate1, eurtoBtcExchangeRate)
      exchangeRateF8eClient.exchangeRates.value = Ok(exchangeRates)

      // no rates synced because app is backgrounded
      delay(syncFrequency)
      awaitNoEvents()
      exchangeRateDao.allExchangeRates.value.shouldBeEmpty()

      appSessionManager.appDidEnterForeground()

      // rates synced because app is foregrounded
      awaitUntil(exchangeRates)
      // database updated
      exchangeRateDao.allExchangeRates.value.shouldContainExactly(exchangeRates)
    }
  }

  test("remote sync occurs when manually requesting sync") {
    createBackgroundScope().launch {
      exchangeRateService.executeWork()
    }

    exchangeRateService.exchangeRates.test {
      awaitItem().shouldBeEmpty()

      exchangeRateF8eClient.exchangeRates.value = Ok(listOf(exchangeRate1))

      exchangeRateService.requestSync()

      awaitItem().shouldContainExactly(listOf(exchangeRate1))
    }
  }

  test("remote sync occurs when entering foreground") {
    createBackgroundScope().launch {
      exchangeRateService.executeWork()
    }

    appSessionManager.appDidEnterBackground()

    exchangeRateService.exchangeRates.test {
      awaitItem().shouldBeEmpty()

      exchangeRateF8eClient.exchangeRates.value = Ok(listOf(exchangeRate1))

      appSessionManager.appDidEnterForeground()

      awaitItem().shouldContainExactly(exchangeRate1)
    }
  }

  test("retrieving existing exchange rates in past 10 mins") {
    val exchangeRates = listOf(exchangeRate1, eurtoBtcExchangeRate)
    exchangeRateF8eClient.exchangeRates.value = Ok(exchangeRates)

    createBackgroundScope().launch {
      exchangeRateService.executeWork()
    }

    exchangeRateService.exchangeRates.test {
      awaitUntil(exchangeRates)

      exchangeRateService.mostRecentRatesSinceDurationForCurrency(10.minutes, USD)
        .shouldNotBeNull()
        .shouldContainExactly(exchangeRates)
    }
  }

  test("retrieving existing exchange rates in past 5 mins") {
    val exchangeRates = listOf(exchangeRate1, eurtoBtcExchangeRate)
    exchangeRateF8eClient.exchangeRates.value = Ok(exchangeRates)

    createBackgroundScope().launch {
      exchangeRateService.executeWork()
    }

    exchangeRateService.exchangeRates.test {
      awaitUntil(exchangeRates)

      exchangeRateService.mostRecentRatesSinceDurationForCurrency(5.minutes, USD)
        .shouldBeNull()
    }
  }

  test("syncs exchange rates for software accounts") {
    accountService.accountState.value = Ok(ActiveAccount(SoftwareAccountMock))
    exchangeRateF8eClient.exchangeRates.value = Ok(listOf(exchangeRate1))

    createBackgroundScope().launch {
      exchangeRateService.executeWork()
    }

    exchangeRateService.exchangeRates.test {
      awaitUntil(listOf(exchangeRate1))
    }
  }

  test("does not sync exchange rates for lite accounts") {
    accountService.accountState.value = Ok(ActiveAccount(LiteAccountMock))
    exchangeRateF8eClient.exchangeRates.value = Ok(listOf(exchangeRate1))

    createBackgroundScope().launch {
      exchangeRateService.executeWork()
    }

    exchangeRateService.exchangeRates.test {
      awaitItem().shouldBeEmpty()
    }
  }
})
