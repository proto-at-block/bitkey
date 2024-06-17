package build.wallet.statemachine.data.mobilepay

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.limit.MobilePayServiceMock
import build.wallet.limit.MobilePayStatus
import build.wallet.limit.SpendingLimitMock
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.transactions.KeyboxTransactionsDataMock
import build.wallet.statemachine.data.mobilepay.MobilePayData.LoadingMobilePayData
import build.wallet.statemachine.data.mobilepay.MobilePayData.MobilePayDisabledData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class MobilePayDataStateMachineImplTests : FunSpec({
  val mobilePayService = MobilePayServiceMock(turbines::create)
  val currencyConverter = CurrencyConverterFake()
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)

  val stateMachine = MobilePayDataStateMachineImpl(
    mobilePayService,
    currencyConverter,
    fiatCurrencyPreferenceRepository
  )

  val account = FullAccountMock
  val limit1 = SpendingLimitMock(amount = FiatMoney.usd(100))
  val fiatLimit2 = FiatMoney.usd(200)
  val limit2 = SpendingLimitMock(amount = fiatLimit2)

  val props = MobilePayProps(
    account = account,
    transactionsData = KeyboxTransactionsDataMock
  )

  beforeTest {
    fiatCurrencyPreferenceRepository.reset()
  }

  test("loading mobile pay data") {
    stateMachine.test(props) {
      awaitItem().shouldBe(LoadingMobilePayData)
    }
  }

  test("mobile pay disabled data, no most recent limit") {
    mobilePayService.status.value =
      MobilePayStatus.MobilePayDisabled(mostRecentSpendingLimit = null)

    stateMachine.test(props) {
      awaitItem().shouldBe(LoadingMobilePayData)

      with(awaitItem().shouldBeTypeOf<MobilePayDisabledData>()) {
        mostRecentSpendingLimit.shouldBeNull()
      }
    }
  }

  test("mobile pay disabled data, with most recent limit") {
    mobilePayService.status.value =
      MobilePayStatus.MobilePayDisabled(mostRecentSpendingLimit = limit1)

    stateMachine.test(props) {
      awaitItem().shouldBe(LoadingMobilePayData)

      with(awaitItem().shouldBeTypeOf<MobilePayDisabledData>()) {
        mostRecentSpendingLimit.shouldBe(limit1)
      }
    }
  }

  test("mobile pay disabled data, enable mobile pay") {
    mobilePayService.status.value =
      MobilePayStatus.MobilePayDisabled(mostRecentSpendingLimit = null)

    stateMachine.test(props) {
      awaitItem().shouldBe(LoadingMobilePayData)

      with(awaitItem().shouldBeTypeOf<MobilePayDisabledData>()) {
        enableMobilePay(limit2, fiatLimit2, HwFactorProofOfPossession("abc"), {})

        mobilePayService.setLimitCalls.awaitItem().shouldBe(limit2)
      }
    }
  }
})
