package build.wallet.statemachine.data.keybox

import build.wallet.analytics.events.AppSessionManagerFake
import build.wallet.bitcoin.balance.BitcoinBalanceFake
import build.wallet.bitcoin.transactions.BitcoinTransactionFake
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitcoin.wallet.shouldBeZero
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.exchange.ExchangeRateSyncerMock
import build.wallet.money.matchers.shouldBeZero
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsData.FullAccountTransactionsLoadedData
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsData.LoadingFullAccountTransactionsData
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsDataProps
import build.wallet.statemachine.data.keybox.transactions.FullAccountTransactionsDataStateMachineImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class FullAccountTransactionsDataStateMachineImplTests : FunSpec({
  val stateMachine = FullAccountTransactionsDataStateMachineImpl(
    currencyConverter = CurrencyConverterFake(),
    fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create),
    appSessionManager = AppSessionManagerFake(),
    exchangeRateSyncer = ExchangeRateSyncerMock(turbines::create)
  )

  val account = FullAccountMock
  val wallet = SpendingWalletMock(turbines::create, account.keybox.activeSpendingKeyset.localId)

  beforeTest {
    wallet.reset()
  }

  test("load balance and transactions") {
    stateMachine.test(props = FullAccountTransactionsDataProps(account, wallet)) {
      awaitItem().shouldBe(LoadingFullAccountTransactionsData)
      wallet.initializeCalls.awaitItem()

      with(awaitItem().shouldBeInstanceOf<FullAccountTransactionsLoadedData>()) {
        balance.shouldBeZero()
        transactions.shouldBeEmpty()
        fiatBalance.shouldBeNull()
      }

      with(awaitItem().shouldBeInstanceOf<FullAccountTransactionsLoadedData>()) {
        balance.shouldBeZero()
        transactions.shouldBeEmpty()
        fiatBalance.shouldNotBeNull().shouldBeZero()
      }

      wallet.transactionsFlow.value = listOf(BitcoinTransactionFake)

      with(awaitItem().shouldBeInstanceOf<FullAccountTransactionsLoadedData>()) {
        balance.shouldBeZero()
        transactions.shouldContainExactly(BitcoinTransactionFake)
      }

      val newBalance = BitcoinBalanceFake
      wallet.balanceFlow.value = newBalance

      with(awaitItem().shouldBeInstanceOf<FullAccountTransactionsLoadedData>()) {
        balance.shouldBe(newBalance)
        transactions.shouldContainExactly(BitcoinTransactionFake)
        fiatBalance.shouldNotBeNull().shouldBeZero()
      }

      with(awaitItem().shouldBeInstanceOf<FullAccountTransactionsLoadedData>()) {
        balance.shouldBe(newBalance)
        transactions.shouldContainExactly(BitcoinTransactionFake)
        fiatBalance.shouldNotBeNull().shouldBe(FiatMoney.usd(dollars = 0.003))
      }
    }
  }

  test("sync") {
    stateMachine.test(props = FullAccountTransactionsDataProps(account, wallet)) {
      awaitItem().shouldBe(LoadingFullAccountTransactionsData)

      wallet.initializeCalls.awaitItem()

      with(awaitItem().shouldBeInstanceOf<FullAccountTransactionsLoadedData>()) {
        balance.shouldBeZero()
        transactions.shouldBeEmpty()

        syncTransactions()
        // TODO(W-3862): record and validate sync event
      }

      with(awaitItem().shouldBeInstanceOf<FullAccountTransactionsLoadedData>()) {
        fiatBalance.shouldNotBeNull().shouldBeZero()
      }
    }
  }
})
