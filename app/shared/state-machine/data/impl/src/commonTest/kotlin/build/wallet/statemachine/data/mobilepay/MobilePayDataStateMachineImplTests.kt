package build.wallet.statemachine.data.mobilepay

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_MOBILE_TRANSACTIONS_ENABLED
import build.wallet.bitcoin.wallet.SpendingWalletMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.limit.MobilePayDisablerMock
import build.wallet.limit.MobilePayLimitSetterMock
import build.wallet.limit.MobilePayStatus
import build.wallet.limit.MobilePayStatusProviderMock
import build.wallet.limit.SpendingLimitMock
import build.wallet.money.FiatMoney
import build.wallet.money.currency.USD
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.transactions.KeyboxTransactionsDataMock
import build.wallet.statemachine.data.mobilepay.MobilePayData.LoadingMobilePayData
import build.wallet.statemachine.data.mobilepay.MobilePayData.MobilePayDisabledData
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class MobilePayDataStateMachineImplTests : FunSpec({
  val mobilePayStatusProvider = MobilePayStatusProviderMock(turbines::create)
  val spendingLimitSetter = MobilePayLimitSetterMock(turbines::create)
  val mobilePayDisabler = MobilePayDisablerMock(turbines::create)
  val eventTracker = EventTrackerMock(turbines::create)
  val currencyConverter = CurrencyConverterFake()

  val stateMachine =
    MobilePayDataStateMachineImpl(
      mobilePayStatusProvider,
      spendingLimitSetter,
      mobilePayDisabler,
      eventTracker,
      currencyConverter
    )

  val account = FullAccountMock
  val spendingWallet = SpendingWalletMock(turbines::create)
  val limit1 = SpendingLimitMock(amount = FiatMoney.usd(100))
  val fiatLimit2 = FiatMoney.usd(200)
  val limit2 = SpendingLimitMock(amount = fiatLimit2)

  val props =
    MobilePayProps(
      account = account,
      spendingWallet = spendingWallet,
      transactionsData = KeyboxTransactionsDataMock,
      fiatCurrency = USD
    )

  test("loading mobile pay data") {
    stateMachine.test(props) {
      awaitItem().shouldBe(LoadingMobilePayData)
    }
  }

  test("mobile pay disabled data, no most recent limit") {
    mobilePayStatusProvider.status.value =
      MobilePayStatus.MobilePayDisabled(mostRecentSpendingLimit = null)

    stateMachine.test(props) {
      awaitItem().shouldBe(LoadingMobilePayData)

      with(awaitItem().shouldBeTypeOf<MobilePayDisabledData>()) {
        mostRecentSpendingLimit.shouldBeNull()
      }
    }
  }

  test("mobile pay disabled data, with most recent limit") {
    mobilePayStatusProvider.status.value =
      MobilePayStatus.MobilePayDisabled(mostRecentSpendingLimit = limit1)

    stateMachine.test(props) {
      awaitItem().shouldBe(LoadingMobilePayData)

      with(awaitItem().shouldBeTypeOf<MobilePayDisabledData>()) {
        mostRecentSpendingLimit.shouldBe(limit1)
      }
    }
  }

  test("mobile pay disabled data, enable mobile pay") {
    mobilePayStatusProvider.status.value =
      MobilePayStatus.MobilePayDisabled(mostRecentSpendingLimit = null)

    stateMachine.test(props) {
      awaitItem().shouldBe(LoadingMobilePayData)

      with(awaitItem().shouldBeTypeOf<MobilePayDisabledData>()) {
        spendingLimitSetter.setLimitResult = Ok(Unit)
        enableMobilePay(limit2, fiatLimit2, HwFactorProofOfPossession("abc"), {})

        eventTracker.eventCalls.awaitItem().shouldBe(
          TrackedAction(ACTION_APP_MOBILE_TRANSACTIONS_ENABLED)
        )
        spendingLimitSetter.setLimitCalls.awaitItem().shouldBe(limit2)
      }
    }
  }
})
