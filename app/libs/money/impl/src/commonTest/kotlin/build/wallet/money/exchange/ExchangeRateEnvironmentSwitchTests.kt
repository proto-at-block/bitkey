@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.money.exchange

import bitkey.account.AccountConfigServiceFake
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus.NoAccount
import build.wallet.f8e.F8eEnvironment
import build.wallet.money.currency.USD
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.platform.config.AppVariant
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Rates are cached locally keyed only by currency pair, so a rate fetched from one
 * F8e environment used to stay readable after switching to another. Against Local
 * F8e — which serves a fixed $22,678/BTC — a Staging rate that survived the switch
 * silently converted fiat amounts at a live market price instead.
 *
 * This reproduced as non-deterministic send amounts in the Mobile Pay QA harness:
 * the same $5 send produced 22,048 sats when the Local rate won the race and
 * ~6,270 sats when the Staging rate did.
 */
class ExchangeRateEnvironmentSwitchTests : FunSpec({
  val exchangeRateDao = ExchangeRateDaoFake()
  val exchangeRateF8eClient = ExchangeRateF8eClientFake()
  val appSessionManager = AppSessionManagerFake()
  val accountService = AccountServiceFake()
  val accountConfigService = AccountConfigServiceFake()
  val testScope = TestScope()

  lateinit var exchangeRateService: ExchangeRateServiceImpl

  // The two rates actually observed in the harness, expressed as BTC-per-USD.
  // Retrieved at the fake clock's "now" so they are fresh for freshness-window reads.
  val stagingRate = USDtoBTC(1.0 / 79_770.0, seconds = 500)
  val localRate = USDtoBTC(1.0 / 22_678.0, seconds = 500)

  beforeTest {
    appSessionManager.reset()
    exchangeRateDao.reset()
    accountService.apply {
      reset()
      // The QA trail switches environment during onboarding, before an account
      // exists, so rates resolve from the default config rather than an account's
      // own (fixed) config.
      accountState.value = Ok(NoAccount)
    }
    accountConfigService.reset()

    exchangeRateService = ExchangeRateServiceImpl(
      exchangeRateDao = exchangeRateDao,
      exchangeRateF8eClient = exchangeRateF8eClient,
      accountService = accountService,
      accountConfigService = accountConfigService,
      appVariant = AppVariant.Development,
      clock = ClockFake(now = Instant.fromEpochSeconds(500)),
      appSessionManager = appSessionManager,
      exchangeRateSyncFrequency = ExchangeRateSyncFrequency(100.milliseconds),
      appScope = testScope
    )
  }

  test("rates from a previous F8e environment are not readable after switching") {
    // Debug builds default to Staging, so the sync worker fetches a live market
    // rate before the QA trail ever reaches the debug menu.
    accountConfigService.setF8eEnvironment(F8eEnvironment.Staging)
    exchangeRateF8eClient.exchangeRates.value = Ok(listOf(stagingRate))
    exchangeRateService.executeWork()
    testScope.runCurrent()

    // The trail then switches to Local F8e.
    accountConfigService.setF8eEnvironment(F8eEnvironment.Local)
    testScope.runCurrent()

    // Before the first Local sync lands, a send flow must not be able to read the
    // stale Staging rate. Previously this returned the Staging rate, converting
    // $5 at ~$79,770/BTC instead of Local's fixed $22,678/BTC.
    exchangeRateService.mostRecentRatesSinceDurationForCurrency(6.minutes, USD)
      .shouldBeNull()
  }

  test("stale rates are dropped from storage once the new environment syncs") {
    accountConfigService.setF8eEnvironment(F8eEnvironment.Staging)
    exchangeRateF8eClient.exchangeRates.value = Ok(listOf(stagingRate))
    exchangeRateService.executeWork()
    testScope.runCurrent()
    exchangeRateDao.allExchangeRates.value.shouldContainExactly(stagingRate)

    accountConfigService.setF8eEnvironment(F8eEnvironment.Local)
    testScope.runCurrent()

    // The next sync against Local must not leave the Staging rate behind, even if
    // Local returns nothing (e.g. the server is unreachable) — a carried-over rate
    // would otherwise become readable again once it looked "fresh".
    exchangeRateF8eClient.exchangeRates.value = Ok(emptyList())
    exchangeRateService.executeWork()
    testScope.runCurrent()

    exchangeRateDao.allExchangeRates.value.shouldBeEmpty()
  }

  test("rates from a previous environment are not readable after a process restart") {
    accountConfigService.setF8eEnvironment(F8eEnvironment.Staging)
    exchangeRateF8eClient.exchangeRates.value = Ok(listOf(stagingRate))
    exchangeRateService.executeWork()
    testScope.runCurrent()

    accountConfigService.setF8eEnvironment(F8eEnvironment.Local)
    testScope.runCurrent()

    // Rate rows persist across launches while in-memory state does not, so a switch
    // followed by a restart must still reject them. A new service instance over the
    // same storage models that restart.
    val restarted = ExchangeRateServiceImpl(
      exchangeRateDao = exchangeRateDao,
      exchangeRateF8eClient = exchangeRateF8eClient,
      accountService = accountService,
      accountConfigService = accountConfigService,
      appVariant = AppVariant.Development,
      clock = ClockFake(now = Instant.fromEpochSeconds(500)),
      appSessionManager = appSessionManager,
      exchangeRateSyncFrequency = ExchangeRateSyncFrequency(100.milliseconds),
      appScope = testScope
    )
    testScope.runCurrent()

    restarted.mostRecentRatesSinceDurationForCurrency(6.minutes, USD)
      .shouldBeNull()
    restarted.exchangeRates.value.shouldBeEmpty()
  }

  test("rates sync normally after an environment switch") {
    accountConfigService.setF8eEnvironment(F8eEnvironment.Staging)
    exchangeRateF8eClient.exchangeRates.value = Ok(listOf(stagingRate))
    exchangeRateService.executeWork()
    testScope.runCurrent()

    accountConfigService.setF8eEnvironment(F8eEnvironment.Local)
    testScope.runCurrent()

    // Once Local F8e responds, its rate is stored and used — invalidation must not
    // wedge syncing for the new environment.
    exchangeRateF8eClient.exchangeRates.value = Ok(listOf(localRate))
    exchangeRateService.executeWork()
    testScope.runCurrent()

    exchangeRateService.exchangeRates.value.shouldContainExactly(localRate)
    exchangeRateService.mostRecentRatesSinceDurationForCurrency(6.minutes, USD)
      .shouldNotBeNull()
      .shouldContainExactly(localRate)
  }
})
