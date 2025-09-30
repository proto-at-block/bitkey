@file:OptIn(DelicateCoroutinesApi::class)

package build.wallet.limit

import app.cash.turbine.test
import bitkey.verification.FakeTxVerificationApproval
import bitkey.verification.VerificationRequiredError
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.account.AccountStatus.OnboardingAccount
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_MOBILE_TRANSACTIONS_DISABLED
import build.wallet.analytics.v1.Action.ACTION_APP_MOBILE_TRANSACTIONS_ENABLED
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitcoin.transactions.TransactionsDataMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.mobilepay.MobilePaySigningF8eClientMock
import build.wallet.f8e.mobilepay.MobilePaySpendingLimitF8eClientMock
import build.wallet.f8e.mobilepay.isServerSigned
import build.wallet.ktor.result.HttpError
import build.wallet.limit.MobilePayData.MobilePayEnabledData
import build.wallet.limit.MobilePayStatus.MobilePayDisabled
import build.wallet.limit.MobilePayStatus.MobilePayEnabled
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney.Companion.eur
import build.wallet.money.FiatMoney.Companion.usd
import build.wallet.money.currency.EUR
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryFake
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.exchange.ExchangeRateServiceFake
import build.wallet.money.exchange.USDtoBTC
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getError
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MobilePayServiceImplTests : FunSpec({
  val eventTracker = EventTrackerMock(turbines::create)
  val spendingLimitDao = SpendingLimitDaoFake()
  val spendingLimitF8eClient = MobilePaySpendingLimitF8eClientMock()
  val mobilePayStatusProvider = MobilePayStatusRepositoryMock(turbines::create)
  val bitcoinWalletService = BitcoinWalletServiceFake()
  val appSessionManager = AppSessionManagerFake()
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryFake()
  val accountService = AccountServiceFake()
  val currencyConverter = CurrencyConverterFake()
  val mobilePaySigningF8eClient = MobilePaySigningF8eClientMock(turbines::create)
  val exchangeRateService = ExchangeRateServiceFake()
  val syncFrequency = 100.milliseconds

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
    remainingBitcoinSpendingAmount = BitcoinMoney.btc(1.0),
    remainingFiatSpendingAmount = usd(300)
  )

  lateinit var mobilePayService: MobilePayServiceImpl

  beforeTest {
    spendingLimitDao.reset()
    appSessionManager.reset()
    bitcoinWalletService.reset()
    mobilePayStatusProvider.reset()
    accountService.reset()
    accountService.setActiveAccount(FullAccountMock)
    currencyConverter.reset()
    fiatCurrencyPreferenceRepository.reset()
    mobilePaySigningF8eClient.reset()
    exchangeRateService.reset()

    mobilePayService = MobilePayServiceImpl(
      eventTracker = eventTracker,
      spendingLimitDao = spendingLimitDao,
      spendingLimitF8eClient = spendingLimitF8eClient,
      mobilePayStatusRepository = mobilePayStatusProvider,
      appSessionManager = appSessionManager,
      bitcoinWalletService = bitcoinWalletService,
      accountService = accountService,
      currencyConverter = currencyConverter,
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
      mobilePaySigningF8eClient = mobilePaySigningF8eClient,
      mobilePaySyncFrequency = MobilePaySyncFrequency(syncFrequency),
      exchangeRateService = exchangeRateService
    )
  }

  val hwPop = HwFactorProofOfPossession("")

  test("executeWork refreshes mobile pay status when transactions are loaded") {
    // Use a larger sync frequency here to ensure we only capture the initial refresh call
    // and not any periodic syncs, otherwise this test can become flaky.
    val testSyncFrequency = 10.seconds
    val testService = MobilePayServiceImpl(
      eventTracker = eventTracker,
      spendingLimitDao = spendingLimitDao,
      spendingLimitF8eClient = spendingLimitF8eClient,
      mobilePayStatusRepository = mobilePayStatusProvider,
      appSessionManager = appSessionManager,
      bitcoinWalletService = bitcoinWalletService,
      accountService = accountService,
      currencyConverter = currencyConverter,
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
      mobilePaySigningF8eClient = mobilePaySigningF8eClient,
      mobilePaySyncFrequency = MobilePaySyncFrequency(testSyncFrequency),
      exchangeRateService = exchangeRateService
    )

    createBackgroundScope().launch {
      testService.executeWork()
    }

    mobilePayStatusProvider.refreshStatusCalls.awaitNoEvents(1.milliseconds)

    bitcoinWalletService.transactionsData.value = TransactionsDataMock
    mobilePayStatusProvider.refreshStatusCalls.awaitItem()
  }

  test("executeWork periodically refreshes mobile pay status") {
    bitcoinWalletService.transactionsData.value = TransactionsDataMock

    createBackgroundScope().launch {
      mobilePayService.executeWork()
    }

    mobilePayStatusProvider.refreshStatusCalls.awaitItem()
    mobilePayStatusProvider.refreshStatusCalls.expectNoEvents()

    mobilePayStatusProvider.refreshStatusCalls.awaitItem()
  }

  test("executeWork periodic sync does not refresh if app is backgrounded") {
    appSessionManager.appDidEnterBackground()
    bitcoinWalletService.transactionsData.value = TransactionsDataMock

    createBackgroundScope().launch {
      mobilePayService.executeWork()
    }

    mobilePayStatusProvider.refreshStatusCalls.awaitNoEvents(syncFrequency)

    appSessionManager.appDidEnterForeground()
    mobilePayStatusProvider.refreshStatusCalls.awaitItem()
  }

  test("enable mobile pay by setting the limit for the first time") {
    mobilePayService.setLimit(SpendingLimitMock, hwPop).shouldBeOk()

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

    mobilePayService.setLimit(SpendingLimitMock, hwPop).shouldBeOk()

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

    mobilePayService.setLimit(SpendingLimitMock2, hwPop).shouldBeOk()

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

    mobilePayService.disable().shouldBeOk()

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
    mobilePayService.disable().shouldBeOk()

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
    mobilePayService.setLimit(SpendingLimitMock, hwPop).shouldBeOk()
    eventTracker.eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_MOBILE_TRANSACTIONS_ENABLED)
    )

    mobilePayService.disable().shouldBeOk()

    // verify that the active spending limit was cleared
    spendingLimitDao.activeSpendingLimit().firstOrNull().shouldBe(null)
    // verify most recent active spending limit
    spendingLimitDao.mostRecentSpendingLimit().shouldBeOk(SpendingLimitMock)

    eventTracker.eventCalls.awaitItem().shouldBe(
      TrackedAction(ACTION_APP_MOBILE_TRANSACTIONS_DISABLED)
    )
  }

  test("mobilePayData defaults to null") {
    createBackgroundScope().launch {
      mobilePayService.executeWork()
    }

    mobilePayService.mobilePayData.test {
      awaitItem().shouldBeNull()
    }
  }

  test("mobilePayData is null if not an active full account") {
    accountService.clear()
    createBackgroundScope().launch {
      mobilePayService.executeWork()
    }
    mobilePayStatusProvider.status.value = mobilePayEnabled

    mobilePayService.mobilePayData.test {
      awaitItem().shouldBeNull()

      accountService.accountState.value = Ok(ActiveAccount(LiteAccountMock))
      awaitNoEvents(syncFrequency)

      accountService.accountState.value = Ok(OnboardingAccount(FullAccountMock))
      awaitNoEvents(syncFrequency)

      accountService.accountState.value = Ok(ActiveAccount(FullAccountMock))
      awaitUntil(mobilePayDataEnabled)

      accountService.accountState.value = Ok(OnboardingAccount(FullAccountMock))
      awaitItem().shouldBeNull()
    }
  }

  test("mobilePayData updates if currency preference changes") {
    createBackgroundScope().launch {
      mobilePayService.executeWork()
    }
    accountService.accountState.value = Ok(ActiveAccount(FullAccountMock))
    mobilePayStatusProvider.status.value = mobilePayEnabled

    mobilePayService.mobilePayData.test {
      awaitUntil(mobilePayDataEnabled)

      fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value = EUR
      awaitItem().shouldBe(
        mobilePayDataEnabled.copy(
          activeSpendingLimit = mobilePayEnabled.activeSpendingLimit.copy(
            // 900 because our fake currency conversion multiplies by 3, and we're starting with a fiat
            // limit of 100, converting it to BTC (300), then converting to euro (900).
            amount = eur(900)
          ),
          remainingFiatSpendingAmount = eur(300)
        )
      )
    }
  }

  test("mobilePayData updates if exchange rates change") {
    createBackgroundScope().launch {
      mobilePayService.executeWork()
    }
    accountService.accountState.value = Ok(ActiveAccount(FullAccountMock))
    mobilePayStatusProvider.status.value = mobilePayEnabled

    mobilePayService.mobilePayData.test {
      awaitUntil(mobilePayDataEnabled)

      // Update the exchange rate
      currencyConverter.conversionRate = 5.0
      // This exchange rate isn't used due to fakes, but changing it should trigger a recalculation.
      exchangeRateService.exchangeRates.value = listOf(USDtoBTC(0.05))
      awaitItem().shouldBe(
        mobilePayDataEnabled.copy(remainingFiatSpendingAmount = usd(500))
      )
    }
  }

  test("mobilePayData updates if underlying mobile pay status changes") {
    createBackgroundScope().launch {
      mobilePayService.executeWork()
    }
    accountService.accountState.value = Ok(ActiveAccount(FullAccountMock))
    mobilePayStatusProvider.status.value = mobilePayEnabled

    mobilePayService.mobilePayData.test {
      awaitUntil(mobilePayDataEnabled)

      mobilePayStatusProvider.status.value =
        MobilePayDisabled(mostRecentSpendingLimit = SpendingLimitMock)
      awaitItem().shouldBe(MobilePayDisabledDataMock)
    }
  }

  test("mobilePayData correctly calculates remainingFiatSpendingAmount") {
    createBackgroundScope().launch {
      mobilePayService.executeWork()
    }
    accountService.accountState.value = Ok(ActiveAccount(FullAccountMock))

    currencyConverter.conversionRate = 3.0 // 1 btc == 3 dollars
    val mobilePayEnabledWithSpentBtc = mobilePayEnabled.copy(
      balance = MobilePayBalance(
        spent = BitcoinMoney.btc(0.5), // spent $1.5
        available = BitcoinMoney.btc(1.5), // $4.5 available
        limit = SpendingLimitMock(usd(dollars = 6.0)) // 6 usd limit
      )
    )
    mobilePayStatusProvider.status.value = mobilePayEnabledWithSpentBtc

    mobilePayService.mobilePayData.test {
      awaitUntil(
        MobilePayEnabledData(
          activeSpendingLimit = mobilePayEnabledWithSpentBtc.activeSpendingLimit,
          remainingBitcoinSpendingAmount = mobilePayEnabledWithSpentBtc.balance!!.available,
          remainingFiatSpendingAmount = usd(dollars = 4.5)
        )
      )
    }
  }

  test("successfully signing a psbt with mobile pay") {
    accountService.setActiveAccount(FullAccountMock)
    mobilePayService.signPsbtWithMobilePay(PsbtMock, null)
      .value
      .isServerSigned()
      .shouldBeTrue()

    mobilePaySigningF8eClient.signWithSpecificKeysetCalls.awaitItem().shouldBeTypeOf<Pair<Psbt, *>>().first.shouldBe(PsbtMock)
  }

  test("error signing a psbt with mobile pay") {
    accountService.setActiveAccount(FullAccountMock)
    mobilePaySigningF8eClient.signWithSpecificKeysetResult =
      Err(HttpError.NetworkError(Error("no sign")))
    mobilePayService.signPsbtWithMobilePay(PsbtMock, null)
      .isErr
      .shouldBeTrue()

    mobilePaySigningF8eClient.signWithSpecificKeysetCalls.awaitItem().shouldBeTypeOf<Pair<Psbt, *>>().first.shouldBe(PsbtMock)
  }

  test("successfully signing a psbt with mobile pay with grant") {
    accountService.setActiveAccount(FullAccountMock)
    mobilePayService.signPsbtWithMobilePay(PsbtMock, FakeTxVerificationApproval)
      .value
      .isServerSigned()
      .shouldBeTrue()

    mobilePaySigningF8eClient.signWithSpecificKeysetCalls.awaitItem().shouldBe(PsbtMock to FakeTxVerificationApproval)
  }

  test("verification required while signing a psbt with mobile pay") {
    accountService.setActiveAccount(FullAccountMock)
    mobilePaySigningF8eClient.signWithSpecificKeysetResult =
      Err(VerificationRequiredError)
    mobilePayService.signPsbtWithMobilePay(PsbtMock, null)
      .getError()
      .shouldNotBeNull()
      .shouldBeInstanceOf<VerificationRequiredError>()

    mobilePaySigningF8eClient.signWithSpecificKeysetCalls.awaitItem().shouldBeTypeOf<Pair<Psbt, *>>().first.shouldBe(PsbtMock)
  }

  test(
    "Given previous transactions and new transaction are below limit, mobile pay is available"
  ) {
    createBackgroundScope().launch {
      mobilePayService.executeWork()
    }
    accountService.accountState.value = Ok(ActiveAccount(FullAccountMock))
    mobilePayStatusProvider.status.value = mobilePayEnabled

    mobilePayService.mobilePayData.test {
      awaitUntil(mobilePayDataEnabled)
    }

    mobilePayService.getDailySpendingLimitStatus(
      transactionAmount = BitcoinMoney.sats(1000)
    ).shouldBe(
      DailySpendingLimitStatus.MobilePayAvailable
    )
  }

  test("Given that transaction amount is above the limit, hardware is required") {
    createBackgroundScope().launch {
      mobilePayService.executeWork()
    }
    accountService.accountState.value = Ok(ActiveAccount(FullAccountMock))
    mobilePayStatusProvider.status.value = mobilePayEnabled

    mobilePayService.mobilePayData.test {
      awaitUntil(mobilePayDataEnabled)
    }

    mobilePayService.getDailySpendingLimitStatus(
      transactionAmount = BitcoinMoney.btc(2.0)
    ).shouldBe(
      DailySpendingLimitStatus.RequiresHardware
    )
  }

  test("Given that balance is null, hardware is required") {
    createBackgroundScope().launch {
      mobilePayService.executeWork()
    }
    accountService.accountState.value = Ok(ActiveAccount(FullAccountMock))
    mobilePayStatusProvider.status.value = MobilePayEnabled(
      activeSpendingLimit = SpendingLimitMock,
      balance = null
    )

    mobilePayService.getDailySpendingLimitStatus(
      transactionAmount = BitcoinMoney.btc(2.0)
    ).shouldBe(
      DailySpendingLimitStatus.RequiresHardware
    )
  }
})
