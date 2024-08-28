package build.wallet.limit

import app.cash.turbine.test
import build.wallet.account.AccountRepositoryFake
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.account.AccountStatus.OnboardingAccount
import build.wallet.analytics.events.AppSessionManagerFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_MOBILE_TRANSACTIONS_DISABLED
import build.wallet.analytics.v1.Action.ACTION_APP_MOBILE_TRANSACTIONS_ENABLED
import build.wallet.bitcoin.transactions.KeyboxTransactionsDataMock
import build.wallet.bitcoin.transactions.TransactionsServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.mobilepay.MobilePaySpendingLimitF8eClientMock
import build.wallet.limit.MobilePayData.MobilePayEnabledData
import build.wallet.limit.MobilePayStatus.MobilePayDisabled
import build.wallet.limit.MobilePayStatus.MobilePayEnabled
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney.Companion.eur
import build.wallet.money.FiatMoney.Companion.usd
import build.wallet.money.currency.EUR
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryFake
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Ok
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.minutes

class MobilePayServiceImplTests : FunSpec({
  coroutineTestScope = true

  val eventTracker = EventTrackerMock(turbines::create)
  val spendingLimitDao = SpendingLimitDaoFake()
  val spendingLimitF8eClient = MobilePaySpendingLimitF8eClientMock()
  val mobilePayStatusProvider = MobilePayStatusRepositoryMock(turbines::create)
  val transactionsService = TransactionsServiceFake()
  val appSessionManager = AppSessionManagerFake()
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryFake()
  val accountRepository = AccountRepositoryFake()
  val currencyConverter = CurrencyConverterFake()

  val mobilePayBalance = MobilePayBalance(
    spent = BitcoinMoney.zero(),
    available = BitcoinMoney.btc(1.0),
    limit = SpendingLimitMock
  )

  val mobilePayEnabled = MobilePayEnabled(
    activeSpendingLimit = SpendingLimitMock,
    balance = mobilePayBalance
  )

  val mobilePayDataEnabled = MobilePayEnabledData(
    activeSpendingLimit = mobilePayEnabled.activeSpendingLimit,
    balance = mobilePayEnabled.balance,
    remainingFiatSpendingAmount = usd(100)
  )

  lateinit var mobilePayService: MobilePayServiceImpl

  beforeTest {
    spendingLimitDao.reset()
    appSessionManager.reset()
    transactionsService.reset()
    accountRepository.reset()
    currencyConverter.reset()
    fiatCurrencyPreferenceRepository.reset()

    mobilePayService = MobilePayServiceImpl(
      eventTracker = eventTracker,
      spendingLimitDao = spendingLimitDao,
      spendingLimitF8eClient = spendingLimitF8eClient,
      mobilePayStatusRepository = mobilePayStatusProvider,
      appSessionManager = appSessionManager,
      transactionsService = transactionsService,
      accountRepository = accountRepository,
      currencyConverter = currencyConverter,
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository
    )
  }

  val hwPop = HwFactorProofOfPossession("")

  test("executeWork refreshes mobile pay status when transactions are loaded") {
    backgroundScope.launch {
      mobilePayService.executeWork()
    }

    mobilePayStatusProvider.refreshStatusCalls.expectNoEvents()

    transactionsService.transactionsData.value = KeyboxTransactionsDataMock
    mobilePayStatusProvider.refreshStatusCalls.awaitItem()
  }

  test("executeWork periodically refreshes mobile pay status") {
    transactionsService.transactionsData.value = KeyboxTransactionsDataMock

    runTest {
      backgroundScope.launch {
        mobilePayService.executeWork()
      }

      runCurrent()
      mobilePayStatusProvider.refreshStatusCalls.awaitItem()

      // Refresh should be called every 30 minutes
      advanceTimeBy(10.minutes)
      mobilePayStatusProvider.refreshStatusCalls.expectNoEvents()
      advanceTimeBy(21.minutes)
      mobilePayStatusProvider.refreshStatusCalls.awaitItem()
    }
  }

  test("executeWork periodic sync does not refresh if app is backgrounded") {
    appSessionManager.appDidEnterBackground()
    transactionsService.transactionsData.value = KeyboxTransactionsDataMock

    runTest {
      backgroundScope.launch {
        mobilePayService.executeWork()
      }

      runCurrent()
      mobilePayStatusProvider.refreshStatusCalls.expectNoEvents()

      advanceTimeBy(31.minutes)
      mobilePayStatusProvider.refreshStatusCalls.expectNoEvents()

      appSessionManager.appDidEnterForeground()
      mobilePayStatusProvider.refreshStatusCalls.awaitItem()
    }
  }

  test("enable mobile pay by setting the limit for the first time") {
    mobilePayService.setLimit(FullAccountMock, SpendingLimitMock, hwPop).shouldBeOk()

    // verify that the active spending limit was set
    spendingLimitDao.activeSpendingLimit().firstOrNull().shouldBe(SpendingLimitMock)
    // verify most recent active spending limit
    spendingLimitDao.mostRecentSpendingLimit().shouldBeOk(SpendingLimitMock)

    eventTracker.eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_MOBILE_TRANSACTIONS_ENABLED)
    )
  }

  test("set the same limit again") {
    // prepopulate database
    spendingLimitDao.saveAndSetSpendingLimit(SpendingLimitMock)

    mobilePayService.setLimit(FullAccountMock, SpendingLimitMock, hwPop).shouldBeOk()

    // verify that the active spending limit was updated
    spendingLimitDao.activeSpendingLimit().firstOrNull().shouldBe(SpendingLimitMock)
    // verify most recent active spending limit
    spendingLimitDao.mostRecentSpendingLimit().shouldBeOk(SpendingLimitMock)

    eventTracker.eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_MOBILE_TRANSACTIONS_ENABLED)
    )
  }

  test("update mobile pay limit") {
    // prepopulate database
    spendingLimitDao.saveAndSetSpendingLimit(SpendingLimitMock)

    mobilePayService.setLimit(FullAccountMock, SpendingLimitMock2, hwPop).shouldBeOk()

    // verify that the active spending limit was updated
    spendingLimitDao.activeSpendingLimit().firstOrNull().shouldBe(SpendingLimitMock2)
    // verify most recent active spending limit
    spendingLimitDao.mostRecentSpendingLimit().shouldBeOk(SpendingLimitMock2)

    eventTracker.eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_MOBILE_TRANSACTIONS_ENABLED)
    )
  }

  test("disable mobile pay when it's enabled") {
    // prepopulate database
    spendingLimitDao.saveAndSetSpendingLimit(SpendingLimitMock)

    mobilePayService.disable(account = FullAccountMock).shouldBeOk()

    // verify that the active spending limit was cleared
    spendingLimitDao.activeSpendingLimit().firstOrNull().shouldBe(null)
    // verify most recent active spending limit
    spendingLimitDao.mostRecentSpendingLimit().shouldBeOk(SpendingLimitMock)

    eventTracker.eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_MOBILE_TRANSACTIONS_DISABLED)
    )
  }

  /**
   * Test that mobile pay is disabled when it's already disabled (there is no active spending limit).
   * This is an edge case that should not be reproducible in UI, but is tested for completeness.
   */
  test("disable mobile pay when it's disabled") {
    mobilePayService.disable(account = FullAccountMock).shouldBeOk()

    // verify that the active spending limit was cleared
    spendingLimitDao.activeSpendingLimit().firstOrNull().shouldBe(null)
    // verify most recent active spending limit
    spendingLimitDao.mostRecentSpendingLimit().shouldBeOk(null)

    eventTracker.eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_MOBILE_TRANSACTIONS_DISABLED)
    )
  }

  test("enable and then disable mobile pay") {
    // setup
    mobilePayService.setLimit(FullAccountMock, SpendingLimitMock, hwPop).shouldBeOk()
    eventTracker.eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_MOBILE_TRANSACTIONS_ENABLED)
    )

    mobilePayService.disable(account = FullAccountMock).shouldBeOk()

    // verify that the active spending limit was cleared
    spendingLimitDao.activeSpendingLimit().firstOrNull().shouldBe(null)
    // verify most recent active spending limit
    spendingLimitDao.mostRecentSpendingLimit().shouldBeOk(SpendingLimitMock)

    eventTracker.eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_MOBILE_TRANSACTIONS_DISABLED)
    )
  }

  test("mobilePayData defaults to null") {
    backgroundScope.launch {
      mobilePayService.executeWork()
    }

    mobilePayService.mobilePayData.test {
      awaitItem().shouldBeNull()
    }
  }

  test("mobilePayData is null if not an active full account") {
    backgroundScope.launch {
      mobilePayService.executeWork()
    }
    mobilePayStatusProvider.status.value = mobilePayEnabled

    mobilePayService.mobilePayData.test {
      awaitItem().shouldBeNull()

      accountRepository.accountState.value = Ok(ActiveAccount(LiteAccountMock))
      expectNoEvents()

      accountRepository.accountState.value = Ok(OnboardingAccount(FullAccountMock))
      expectNoEvents()

      accountRepository.accountState.value = Ok(ActiveAccount(FullAccountMock))
      awaitItem().shouldBe(mobilePayDataEnabled)

      accountRepository.accountState.value = Ok(OnboardingAccount(FullAccountMock))
      awaitItem().shouldBeNull()
    }
  }

  test("mobilePayData updates if currency preference changes") {
    backgroundScope.launch {
      mobilePayService.executeWork()
    }
    accountRepository.accountState.value = Ok(ActiveAccount(FullAccountMock))
    mobilePayStatusProvider.status.value = mobilePayEnabled

    mobilePayService.mobilePayData.test {
      awaitItem().shouldBeNull()

      awaitItem().shouldBe(mobilePayDataEnabled)

      fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value = EUR
      awaitItem().shouldBe(mobilePayDataEnabled.copy(remainingFiatSpendingAmount = eur(100)))
    }
  }

  test("mobilePayData updates if underlying mobile pay status changes") {
    backgroundScope.launch {
      mobilePayService.executeWork()
    }
    accountRepository.accountState.value = Ok(ActiveAccount(FullAccountMock))
    mobilePayStatusProvider.status.value = mobilePayEnabled

    mobilePayService.mobilePayData.test {
      awaitItem().shouldBeNull()

      awaitItem().shouldBe(mobilePayDataEnabled)

      mobilePayStatusProvider.status.value =
        MobilePayDisabled(mostRecentSpendingLimit = SpendingLimitMock)
      awaitItem().shouldBe(MobilePayDisabledDataMock)
    }
  }

  test("mobilePayData correctly calculates remainingFiatSpendingAmount") {
    backgroundScope.launch {
      mobilePayService.executeWork()
    }
    accountRepository.accountState.value = Ok(ActiveAccount(FullAccountMock))

    currencyConverter.conversionRate = 3.0 // 1 btc == 3 dollars
    val mobilePayEnabledWithSpentBtc = mobilePayEnabled.copy(
      balance = MobilePayBalance(
        spent = BitcoinMoney.btc(1.0), // spent 3 usd
        available = BitcoinMoney.btc(2.0),
        limit = SpendingLimitMock(usd(dollars = 6.0)) // 6 usd available
      )
    )
    mobilePayStatusProvider.status.value = mobilePayEnabledWithSpentBtc

    mobilePayService.mobilePayData.test {
      awaitItem().shouldBeNull()

      awaitItem().shouldBe(
        MobilePayEnabledData(
          activeSpendingLimit = mobilePayEnabledWithSpentBtc.activeSpendingLimit,
          balance = mobilePayEnabledWithSpentBtc.balance,
          remainingFiatSpendingAmount = usd(dollars = 3.0)
        )
      )
    }
  }
})
