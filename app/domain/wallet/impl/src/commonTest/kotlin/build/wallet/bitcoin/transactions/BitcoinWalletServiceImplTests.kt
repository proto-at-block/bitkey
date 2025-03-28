@file:OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)

package build.wallet.bitcoin.transactions

import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.bitcoin.address.someBitcoinAddress
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.blockchain.BitcoinBlockchainMock
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimatorMock
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitcoin.wallet.shouldBeZero
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock2
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.awaitUntilNotNull
import build.wallet.coroutines.turbine.turbines
import build.wallet.keybox.wallet.AppSpendingWalletProviderMock
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.EUR
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.exchange.EURtoBTC
import build.wallet.money.exchange.ExchangeRateServiceFake
import build.wallet.money.exchange.USDtoBTC
import build.wallet.money.matchers.shouldBeZero
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.platform.app.AppSessionState
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import build.wallet.time.someInstant
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class BitcoinWalletServiceImplTests : FunSpec({

  val account = FullAccountMock
  val wallet = SpendingWalletMock(turbines::create, account.keybox.activeSpendingKeyset.localId)

  val newAccount = account.copy(keybox = KeyboxMock2)
  val newWallet =
    SpendingWalletMock(turbines::create, newAccount.keybox.activeSpendingKeyset.localId)

  val accountService = AccountServiceFake()
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val exchangeRateService = ExchangeRateServiceFake()
  val outgoingTransactionDetailDao = OutgoingTransactionDetailDaoMock(turbines::create)
  val clock = ClockFake()
  val bitcoinBlockchain = BitcoinBlockchainMock(turbines::create, clock)
  val appSessionManager = AppSessionManagerFake()
  val currencyConverter = CurrencyConverterFake()
  val appSpendingWalletProvider = AppSpendingWalletProviderMock(wallet)
  val syncFrequency = 100.milliseconds

  lateinit var service: BitcoinWalletServiceImpl

  beforeTest {
    service = BitcoinWalletServiceImpl(
      currencyConverter = currencyConverter,
      accountService = accountService,
      appSpendingWalletProvider = appSpendingWalletProvider,
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
      appSessionManager = appSessionManager,
      exchangeRateService = exchangeRateService,
      outgoingTransactionDetailDao = outgoingTransactionDetailDao,
      bitcoinBlockchain = bitcoinBlockchain,
      feeRateEstimator = BitcoinFeeRateEstimatorMock(),
      bitcoinWalletSyncFrequency = BitcoinWalletSyncFrequency(syncFrequency)
    )
    clock.reset()
    wallet.reset()
    outgoingTransactionDetailDao.reset()
    fiatCurrencyPreferenceRepository.reset()
    appSessionManager.reset()
    currencyConverter.reset()
    exchangeRateService.reset()
    bitcoinBlockchain.reset()
    appSpendingWalletProvider.spendingWallet = wallet

    accountService.reset()
    accountService.accountState.value = Ok(ActiveAccount(account))
  }

  test("transactions data updates appropriately") {
    createBackgroundScope().launch {
      service.executeWork()
    }

    service.transactionsData().test {
      wallet.initializeCalls.awaitItem()
      wallet.launchPeriodicSyncCalls.awaitItem()
      wallet.syncCalls.awaitItem()

      awaitUntilNotNull().apply {
        balance.shouldBeZero()
        transactions.shouldBeEmpty()
        fiatBalance.shouldNotBeNull().shouldBeZero()
      }

      // Update a transaction
      wallet.transactionsFlow.value = listOf(BitcoinTransactionFake)

      with(awaitItem().shouldNotBeNull()) {
        balance.shouldBeZero()
        transactions.shouldContainExactly(BitcoinTransactionFake)
      }

      // Update the balance
      wallet.balanceFlow.value = BitcoinBalanceFake

      with(awaitItem().shouldNotBeNull()) {
        balance.shouldBe(BitcoinBalanceFake)
        transactions.run { shouldContainExactly(BitcoinTransactionFake) }
        fiatBalance.shouldBe(FiatMoney.usd(dollars = 0.003))
      }

      // Update the currency preference
      fiatCurrencyPreferenceRepository.internalFiatCurrencyPreference.value = EUR
      with(awaitItem().shouldNotBeNull()) {
        balance.shouldBe(BitcoinBalanceFake)
        transactions.shouldContainExactly(BitcoinTransactionFake)
        fiatBalance.shouldNotBeNull().shouldBe(FiatMoney.eur(0.003))
      }

      // Update the exchange rate
      currencyConverter.conversionRate = 5.0
      // This exchange rate isn't used due to fakes, but changing it should trigger a recalculation.
      exchangeRateService.exchangeRates.value = listOf(EURtoBTC(0.05))
      with(awaitItem().shouldNotBeNull()) {
        balance.shouldBe(BitcoinBalanceFake)
        transactions.shouldContainExactly(BitcoinTransactionFake)
        fiatBalance.shouldNotBeNull().shouldBe(FiatMoney.eur(0.005))
      }
    }
  }

  test("no exchange rate returns null fiat balance") {
    // Fake a missing exchange rate
    currencyConverter.conversionRate = null

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.transactionsData().test {
      wallet.initializeCalls.awaitItem()
      wallet.launchPeriodicSyncCalls.awaitItem()
      wallet.syncCalls.awaitItem()

      awaitUntil { it != null }

      wallet.balanceFlow.value = BitcoinBalanceFake
      with(awaitItem().shouldNotBeNull()) {
        balance.shouldBe(BitcoinBalanceFake)
        transactions.shouldBeEmpty()
        fiatBalance.shouldBeNull()
      }

      // Restore the fake conversation rate
      currencyConverter.reset()
      // This exchange rate isn't used due to fakes, but changing it should trigger a recalculation.
      exchangeRateService.exchangeRates.value = listOf(USDtoBTC(0.03))
      with(awaitItem().shouldNotBeNull()) {
        balance.shouldBe(BitcoinBalanceFake)
        fiatBalance.shouldNotBeNull().shouldBe(FiatMoney.usd(0.003))
      }
    }
  }

  test("sync transactions") {
    createBackgroundScope().launch {
      service.executeWork()
    }

    service.transactionsData().test {
      wallet.initializeCalls.awaitItem()
      wallet.launchPeriodicSyncCalls.awaitItem()
      wallet.syncCalls.awaitItem()

      awaitUntilNotNull().apply {
        balance.shouldBeZero()
        transactions.shouldBeEmpty()
      }

      service.sync()

      wallet.syncCalls.awaitItem()
    }
  }

  test("initialize wallet and launch periodic sync") {
    createBackgroundScope().launch {
      service.executeWork()
    }

    wallet.initializeCalls.awaitItem()
    wallet.launchPeriodicSyncCalls.awaitItem()
    wallet.syncCalls.awaitItem()

    // Changing the account should retrigger syncs
    appSpendingWalletProvider.spendingWallet = newWallet
    accountService.accountState.value = Ok(ActiveAccount(newAccount))

    newWallet.initializeCalls.awaitItem().shouldBe(Unit)
    newWallet.launchPeriodicSyncCalls.awaitItem()
    newWallet.syncCalls.awaitItem()
  }

  test("foregrounding the app should cause an immediate sync") {
    appSessionManager.appSessionState.value = AppSessionState.BACKGROUND
    val backgroundScope = createBackgroundScope()
    backgroundScope.launch {
      service.executeWork()
    }

    wallet.initializeCalls.awaitItem()
    wallet.launchPeriodicSyncCalls.awaitItem()

    // Background & foreground the app should trigger an immediate call to sync
    backgroundScope.launch {
      appSessionManager.appSessionState.value = AppSessionState.FOREGROUND
    }

    wallet.syncCalls.awaitItem()
  }

  test("wallet is updated when keyset changes") {
    createBackgroundScope().launch {
      service.executeWork()
    }
    wallet.initializeCalls.awaitItem()
    wallet.launchPeriodicSyncCalls.awaitItem()
    wallet.syncCalls.awaitItem()

    service.spendingWallet().test {
      awaitUntil(wallet)

      appSpendingWalletProvider.spendingWallet = newWallet
      accountService.accountState.value = Ok(ActiveAccount(newAccount))

      awaitItem().shouldBe(newWallet)
      newWallet.initializeCalls.awaitItem()
      newWallet.launchPeriodicSyncCalls.awaitItem()
      newWallet.syncCalls.awaitItem()
    }
  }

  test("wallet is not updated when keyset do not change") {
    createBackgroundScope().launch {
      service.executeWork()
    }

    service.spendingWallet().test {
      awaitUntil(wallet)
      wallet.initializeCalls.awaitItem()
      wallet.launchPeriodicSyncCalls.awaitItem()
      wallet.syncCalls.awaitItem()

      // Change some account data but keep the spending keys
      val differentAccountSameKeys = newAccount.copy(accountId = FullAccountId("new-account-id"))
      accountService.accountState.value = Ok(ActiveAccount(differentAccountSameKeys))
      delay(syncFrequency)
      awaitNoEvents()

      wallet.initializeCalls.awaitItem()
      wallet.launchPeriodicSyncCalls.awaitItem()
    }
  }

  test("broadcast transaction") {
    createBackgroundScope().launch {
      service.executeWork()
    }
    wallet.initializeCalls.awaitItem()
    wallet.launchPeriodicSyncCalls.awaitItem()
    wallet.syncCalls.awaitItem()

    service.broadcast(PsbtMock, estimatedTransactionPriority = FASTEST).shouldBeOk()

    wallet.syncCalls.awaitItem()

    bitcoinBlockchain.broadcastCalls.awaitItem().shouldBe(PsbtMock)
    outgoingTransactionDetailDao.insertCalls.awaitItem()
    outgoingTransactionDetailDao.broadcastTimeForTransaction("abcdef")
      .shouldBe(someInstant)
    outgoingTransactionDetailDao.confirmationTimeForTransaction("abcdef")
      .shouldBe(someInstant + 10.minutes)
  }

  test("load transactions data") {
    createBackgroundScope().launch {
      service.executeWork()
    }

    wallet.initializeCalls.awaitItem()
    wallet.launchPeriodicSyncCalls.awaitItem()
    wallet.syncCalls.awaitItem()

    service.transactionsData().test {
      awaitUntil { it != null }
    }
  }

  test("building psbts for all transaction priorities") {
    createBackgroundScope().launch {
      service.executeWork()
    }

    service.transactionsData().test {
      wallet.initializeCalls.awaitItem()
      wallet.launchPeriodicSyncCalls.awaitItem()
      wallet.syncCalls.awaitItem()
      // loaded otNull()
      awaitUntil { it != null }

      val sendAmount = BitcoinTransactionSendAmount.ExactAmount(
        money = BitcoinMoney.sats(1_000_000)
      )

      service.createPsbtsForSendAmount(sendAmount, someBitcoinAddress)
        .shouldBeOk()
        .size
        .shouldBe(3)
    }
  }

  test("building psbts for all transaction priorities errors if psbts not created") {
    createBackgroundScope().launch {
      service.executeWork()
    }

    wallet.createSignedPsbtResult = Err(Error())

    service.transactionsData().test {
      wallet.initializeCalls.awaitItem()
      wallet.launchPeriodicSyncCalls.awaitItem()
      wallet.syncCalls.awaitItem()
      // loaded data
      awaitUntil { it != null }

      val sendAmount = BitcoinTransactionSendAmount.ExactAmount(
        money = BitcoinMoney.sats(1_000_000)
      )

      service.createPsbtsForSendAmount(sendAmount, someBitcoinAddress)
        .shouldBeErr(Error("Error creating PSBT for 60 minutes"))
    }
  }
})
