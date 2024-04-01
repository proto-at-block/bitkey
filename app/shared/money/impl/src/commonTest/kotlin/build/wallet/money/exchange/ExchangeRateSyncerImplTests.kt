package build.wallet.money.exchange

import build.wallet.coroutines.advanceTimeBy
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.ActiveF8eEnvironmentRepositoryMock
import build.wallet.ktor.result.HttpError.UnhandledException
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.seconds

class ExchangeRateSyncerImplTests : FunSpec({
  val exchangeRateDao = ExchangeRateDaoMock(turbines::create)
  val f8eExchangeRateService = F8eExchangeRateServiceMock()
  val activeF8eEnvironmentRepository =
    ActiveF8eEnvironmentRepositoryMock(turbines::create)

  val syncer =
    ExchangeRateSyncerImpl(
      exchangeRateDao = exchangeRateDao,
      f8eExchangeRateService = f8eExchangeRateService,
      activeF8eEnvironmentRepository = activeF8eEnvironmentRepository
    )

  val exchangeRate1 = USDtoBTC(0.5)
  val exchangeRate2 = USDtoBTC(1.0)
  val eurtoBtcExchangeRate = EURtoBTC(0.7)

  test("sync immediately") {
    runTest {
      syncer.launchSync(scope = backgroundScope, syncFrequency = 3.seconds)

//      bitstampExchangeRateService.btcToUsdExchangeRate.value = Ok(exchangeRate1)
      activeF8eEnvironmentRepository.activeF8eEnvironmentCalls.awaitItem()
      f8eExchangeRateService.exchangeRates.value = Ok(listOf(exchangeRate1))
      exchangeRateDao.storeExchangeRateCalls.awaitItem().shouldBe(exchangeRate1)
    }
  }

  test("ignore sync failure") {
    runTest {
      backgroundScope.launch {
        syncer.launchSync(scope = this, syncFrequency = 3.seconds)
      }
      f8eExchangeRateService.exchangeRates.value = Err(UnhandledException(Exception("oops")))

      runCurrent()

      advanceTimeBy(3.seconds)
      activeF8eEnvironmentRepository.activeF8eEnvironmentCalls.awaitItem()
      exchangeRateDao.storeExchangeRateCalls.expectNoEvents()

      advanceTimeBy(1.seconds)
      // 3 seconds haven't passed yet.
      activeF8eEnvironmentRepository.activeF8eEnvironmentCalls.awaitItem()
      exchangeRateDao.storeExchangeRateCalls.expectNoEvents()

      f8eExchangeRateService.exchangeRates.value = Ok(listOf(exchangeRate1))

      advanceTimeBy(2.seconds)
      activeF8eEnvironmentRepository.activeF8eEnvironmentCalls.awaitItem()
      exchangeRateDao.storeExchangeRateCalls.awaitItem().shouldBe(exchangeRate1)
    }
  }

  test("sync after frequency duration") {
    runTest {
      backgroundScope.launch {
        syncer.launchSync(scope = this, syncFrequency = 3.seconds)
      }
      f8eExchangeRateService.exchangeRates.value = Ok(listOf(exchangeRate1))

      runCurrent()

      activeF8eEnvironmentRepository.activeF8eEnvironmentCalls.awaitItem()
      exchangeRateDao.storeExchangeRateCalls.awaitItem().shouldBe(exchangeRate1)

      // Update the exchange rate response.
      f8eExchangeRateService.exchangeRates.value = Ok(listOf(exchangeRate2))

      // 3 seconds total haven't passed yet.
      advanceTimeBy(2.seconds)
      exchangeRateDao.storeExchangeRateCalls.expectNoEvents()

      // 3 seconds total have passed.
      advanceTimeBy(1.seconds)
      activeF8eEnvironmentRepository.activeF8eEnvironmentCalls.awaitItem()
      exchangeRateDao.storeExchangeRateCalls.awaitItem().shouldBe(exchangeRate2)

      // Update the exchange rate response.
      f8eExchangeRateService.exchangeRates.value = Ok(listOf(exchangeRate1))

      // Another sync after 3 seconds.
      advanceTimeBy(3.seconds)
      activeF8eEnvironmentRepository.activeF8eEnvironmentCalls.awaitItem()
      exchangeRateDao.storeExchangeRateCalls.awaitItem().shouldBe(exchangeRate1)
    }
  }

  test("sync multiple currencies immediately") {
    runTest {
      val exchangeRates = listOf(exchangeRate1, eurtoBtcExchangeRate)
      f8eExchangeRateService.exchangeRates.value = Ok(exchangeRates)

      syncer.launchSync(scope = backgroundScope, syncFrequency = 3.seconds)

      activeF8eEnvironmentRepository.activeF8eEnvironmentCalls.awaitItem()
      exchangeRates.forEach {
        exchangeRateDao.storeExchangeRateCalls.awaitItem().shouldBe(it)
      }
    }
  }
})
