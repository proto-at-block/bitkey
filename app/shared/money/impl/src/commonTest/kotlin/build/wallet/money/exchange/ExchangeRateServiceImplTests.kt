@file:OptIn(ExperimentalStdlibApi::class, ExperimentalCoroutinesApi::class)

package build.wallet.money.exchange

import app.cash.turbine.test
import build.wallet.analytics.events.AppSessionManagerFake
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.ktor.result.HttpError.UnhandledException
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

class ExchangeRateServiceImplTests : FunSpec({
  coroutineTestScope = true
  val exchangeRateDao = ExchangeRateDaoFake()
  val exchangeRateF8eClient = ExchangeRateF8eClientMock()
  val appSessionManager = AppSessionManagerFake()
  val keyboxDao = KeyboxDaoMock(turbines::create)

  lateinit var exchangeRateService: ExchangeRateServiceImpl

  val exchangeRate1 = USDtoBTC(0.5)
  val exchangeRate2 = USDtoBTC(1.0)
  val eurtoBtcExchangeRate = EURtoBTC(0.7)

  beforeTest {
    appSessionManager.reset()
    exchangeRateDao.reset()
    exchangeRateF8eClient.apply {
      exchangeRates.value = Ok(emptyList())
    }
    keyboxDao.apply {
      reset()
      activeKeybox.value = Ok(KeyboxMock)
    }

    exchangeRateService = ExchangeRateServiceImpl(
      exchangeRateDao = exchangeRateDao,
      exchangeRateF8eClient = exchangeRateF8eClient,
      appSessionManager = appSessionManager,
      keyboxDao = keyboxDao
    )
  }

  test("default value is empty rates") {
    exchangeRateService.exchangeRates.test {
      awaitItem().shouldBeEmpty()
    }
  }

  test("sync immediately") {
    backgroundScope.launch {
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
    backgroundScope.launch {
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
    backgroundScope.launch {
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

    backgroundScope.launch {
      exchangeRateService.executeWork()
    }

    exchangeRateService.exchangeRates.test {
      awaitItem().shouldBeEmpty() // Initial value
      // multiple rates stored
      awaitItem().shouldContainExactly(exchangeRates)
      // database updated
      exchangeRateDao.allExchangeRates.value.shouldContainExactly(exchangeRates)
    }
  }

  test("sync does not occur while app is backgrounded and resumes once foregrounded") {
    val exchangeRates = listOf(exchangeRate1, eurtoBtcExchangeRate)
    appSessionManager.appDidEnterBackground()

    backgroundScope.launch {
      exchangeRateService.executeWork()
    }

    exchangeRateService.exchangeRates.test {
      awaitItem().shouldBeEmpty() // Initial value

      exchangeRateF8eClient.exchangeRates.value = Ok(exchangeRates)

      // no rates synced because app is backgrounded
      expectNoEvents()
      exchangeRateDao.allExchangeRates.value.shouldBeEmpty()

      appSessionManager.appDidEnterForeground()

      // rates synced because app is foregrounded
      awaitItem().shouldContainExactly(exchangeRates)
      // database updated
      exchangeRateDao.allExchangeRates.value.shouldContainExactly(exchangeRates)
    }
  }
})