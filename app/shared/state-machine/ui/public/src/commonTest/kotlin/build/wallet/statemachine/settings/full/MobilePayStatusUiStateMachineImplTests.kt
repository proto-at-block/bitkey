package build.wallet.statemachine.settings.full

import app.cash.turbine.plusAssign
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.limit.*
import build.wallet.limit.MobilePayData.MobilePayDisabledData
import build.wallet.money.FiatMoney
import build.wallet.money.currency.EUR
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitBody
import build.wallet.statemachine.core.test
import build.wallet.statemachine.settings.full.mobilepay.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class MobilePayStatusUiStateMachineImplTests : FunSpec({

  val onSetLimitClickCalls = turbines.create<SpendingLimit?>("set limit click calls")

  fun mobilePayEnabledData(activeSpendingLimit: SpendingLimit) =
    MobilePayData.MobilePayEnabledData(
      activeSpendingLimit = activeSpendingLimit,
      balance = MobilePayBalanceMock,
      remainingFiatSpendingAmount = FiatMoney.usd(100)
    )

  fun mobilePayDisabledData(mostRecentSpendingLimit: SpendingLimit?) =
    MobilePayDisabledData(
      mostRecentSpendingLimit = mostRecentSpendingLimit
    )

  val props =
    MobilePayUiProps(
      onBack = {},
      account = FullAccountMock,
      onSetLimitClick = { currentLimit ->
        onSetLimitClickCalls += currentLimit
      }
    )

  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val mobilePayService = MobilePayServiceMock(turbines::create)

  val stateMachine =
    MobilePayStatusUiStateMachineImpl(
      moneyDisplayFormatter = MoneyDisplayFormatterFake,
      spendingLimitCardUiStateMachine =
        object : SpendingLimitCardUiStateMachine,
          StateMachineMock<SpendingLimitCardUiProps, SpendingLimitCardModel>(
            initialModel =
              SpendingLimitCardModel(
                titleText = "cursus",
                dailyResetTimezoneText = "conclusionemque",
                spentAmountText = "mollis",
                remainingAmountText = "noluisse",
                progressPercentage = 0.1f
              )
          ) {},
      fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
      mobilePayService = mobilePayService
    )

  beforeTest {
    fiatCurrencyPreferenceRepository.reset()
    mobilePayService.reset()
  }

  test("load mobile pay data") {
    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      mobilePayService.mobilePayData.value = mobilePayEnabledData(activeSpendingLimit = SpendingLimitMock)

      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeTrue()
      }
    }
  }

  test("initial state - without existing spending limit") {
    mobilePayService.mobilePayData.value = mobilePayDisabledData(mostRecentSpendingLimit = null)
    stateMachine.test(props) {
      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeFalse()
      }
    }
  }

  test("enable without existing spending limit -> set spending limit") {
    mobilePayService.mobilePayData.value = mobilePayDisabledData(mostRecentSpendingLimit = null)

    stateMachine.test(props) {
      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeFalse()
        switchCardModel.switchModel.onCheckedChange(true)
      }

      onSetLimitClickCalls.awaitItem().shouldBeNull()
    }
  }

  test("initial state - with existing spending limit") {
    mobilePayService.mobilePayData.value = mobilePayEnabledData(activeSpendingLimit = SpendingLimitMock)
    stateMachine.test(props) {
      // Should start with enabled model because that is what's passed in
      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeTrue()
        disableAlertModel.shouldBeNull()
      }

      mobilePayService.mobilePayData.value = mobilePayDisabledData(mostRecentSpendingLimit = null)

      // And then it should update from latest props
      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeFalse()
      }
    }
  }

  test("enabling -> disabling mobile pay with existing limit") {
    mobilePayService.mobilePayData.value = mobilePayEnabledData(activeSpendingLimit = SpendingLimitMock)

    stateMachine.test(props) {
      // Showing limits - Enabled state with existing limit and Daily spend
      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeTrue()
        with(switchCardModel.actionRows.first()) {
          title.shouldBe("Daily limit")
          sideText.shouldBe("$1.00")
        }
        spendingLimitCardModel.shouldNotBeNull()
        switchCardModel.switchModel.onCheckedChange(true)
      }

      // Showing alert
      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeTrue()
        disableAlertModel.shouldNotBeNull().onPrimaryButtonClick()
        mobilePayService.disableCalls.awaitItem()
      }

      // The alert should be dismissed
      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeTrue()
        disableAlertModel.shouldBeNull()
      }
    }
  }

  test("enabling -> dismiss disabling mobile pay with existing limit") {
    mobilePayService.mobilePayData.value = mobilePayEnabledData(activeSpendingLimit = SpendingLimitMock)

    stateMachine.test(props) {
      // Showing limits - Enabled state with existing limit and Daily spend
      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeTrue()
        with(switchCardModel.actionRows.first()) {
          title.shouldBe("Daily limit")
          sideText.shouldBe("$1.00")
        }
        spendingLimitCardModel.shouldNotBeNull()
        switchCardModel.switchModel.onCheckedChange(true)
      }

      // Showing limits - Enabled with confirmCancellation
      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeTrue()
        val alertModel = disableAlertModel.shouldNotBeNull()
        alertModel.onDismiss()
      }

      // Showing limits - Enabled without confirmCancellation
      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeTrue()
        disableAlertModel.shouldBeNull()
      }
    }
  }

  test("disabled -> enable mobile pay with matching currency") {
    mobilePayService.mobilePayData.value = mobilePayDisabledData(mostRecentSpendingLimit = SpendingLimitMock)

    stateMachine.test(props) {
      // Showing limits - Disabled state
      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeFalse()
        spendingLimitCardModel.shouldBeNull()
        switchCardModel.switchModel.onCheckedChange(true)
      }

      onSetLimitClickCalls.awaitItem().shouldBe(SpendingLimitMock)
    }
  }

  test("disabled -> enable mobile pay with different currency") {
    fiatCurrencyPreferenceRepository.internalFiatCurrencyPreference.value = EUR
    mobilePayService.mobilePayData.value = mobilePayDisabledData(mostRecentSpendingLimit = SpendingLimitMock)

    stateMachine.test(props) {
      // Showing limits - Disabled state
      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeFalse()
        spendingLimitCardModel.shouldBeNull()
        switchCardModel.switchModel.onCheckedChange(true)
      }

      onSetLimitClickCalls.awaitItem().shouldBeNull()
    }
  }
})
