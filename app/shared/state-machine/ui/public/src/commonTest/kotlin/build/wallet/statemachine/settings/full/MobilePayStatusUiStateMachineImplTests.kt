package build.wallet.statemachine.settings.full

import app.cash.turbine.plusAssign
import build.wallet.coroutines.turbine.turbines
import build.wallet.limit.MobilePayBalanceMock
import build.wallet.limit.SpendingLimit
import build.wallet.limit.SpendingLimitMock
import build.wallet.money.FiatMoney
import build.wallet.money.currency.EUR
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.currency.USD
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.awaitBody
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.data.mobilepay.MobilePayData
import build.wallet.statemachine.data.mobilepay.MobilePayData.LoadingMobilePayData
import build.wallet.statemachine.data.mobilepay.MobilePayData.MobilePayDisabledData
import build.wallet.statemachine.data.mobilepay.MobilePayData.MobilePayEnabledData
import build.wallet.statemachine.settings.full.mobilepay.MobilePayStatusModel
import build.wallet.statemachine.settings.full.mobilepay.MobilePayStatusUiStateMachineImpl
import build.wallet.statemachine.settings.full.mobilepay.MobilePayUiProps
import build.wallet.statemachine.settings.full.mobilepay.SpendingLimitCardModel
import build.wallet.statemachine.settings.full.mobilepay.SpendingLimitCardUiProps
import build.wallet.statemachine.settings.full.mobilepay.SpendingLimitCardUiStateMachine
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class MobilePayStatusUiStateMachineImplTests : FunSpec({

  val onSetLimitClickCalls = turbines.create<SpendingLimit?>("set limit click calls")
  val disableMobilePayCalls = turbines.create<Unit>("disable mobile pay calls")

  fun MobilePayEnabledData(activeSpendingLimit: SpendingLimit) =
    MobilePayEnabledData(
      activeSpendingLimit = activeSpendingLimit,
      balance = MobilePayBalanceMock,
      remainingFiatSpendingAmount = FiatMoney.usd(100),
      disableMobilePay = { disableMobilePayCalls += Unit },
      changeSpendingLimit = { _, _, _, _ -> },
      refreshBalance = {}
    )

  fun MobilePayDisabledData(mostRecentSpendingLimit: SpendingLimit?) =
    MobilePayDisabledData(
      mostRecentSpendingLimit = mostRecentSpendingLimit,
      enableMobilePay = { _, _, _, _ -> }
    )

  fun props(
    mobilePayData: MobilePayData,
    fiatCurrency: FiatCurrency = USD,
  ) = MobilePayUiProps(
    onBack = {},
    accountData = ActiveKeyboxLoadedDataMock.copy(mobilePayData = mobilePayData),
    fiatCurrency = fiatCurrency,
    onSetLimitClick = { currentLimit ->
      onSetLimitClickCalls += currentLimit
    }
  )

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
          ) {}
    )

  test("load mobile pay data") {
    stateMachine.test(props(mobilePayData = LoadingMobilePayData)) {
      awaitBody<LoadingBodyModel>()

      updateProps(
        props(MobilePayEnabledData(activeSpendingLimit = SpendingLimitMock))
      )

      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeTrue()
      }
    }
  }

  test("initial state - without existing spending limit") {
    stateMachine.test(
      props(mobilePayData = MobilePayDisabledData(mostRecentSpendingLimit = null))
    ) {
      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeFalse()
      }
    }
  }

  test("enable without existing spending limit -> set spending limit") {
    stateMachine.test(
      props(mobilePayData = MobilePayDisabledData(mostRecentSpendingLimit = null))
    ) {
      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeFalse()
        switchCardModel.switchModel.onCheckedChange(true)
      }

      onSetLimitClickCalls.awaitItem().shouldBeNull()
    }
  }

  test("initial state - with existing spending limit") {
    stateMachine.test(
      props(
        mobilePayData = MobilePayEnabledData(activeSpendingLimit = SpendingLimitMock)
      )
    ) {
      // Should start with enabled model because that is what's passed in
      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeTrue()
        disableAlertModel.shouldBeNull()
      }

      updateProps(
        props(
          mobilePayData = MobilePayDisabledData(mostRecentSpendingLimit = null)
        )
      )

      // And then it should update from latest props
      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeFalse()
      }
    }
  }

  test("enabling -> disabling mobile pay with existing limit") {
    stateMachine.test(
      props(
        mobilePayData = MobilePayEnabledData(activeSpendingLimit = SpendingLimitMock)
      )
    ) {
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
        disableMobilePayCalls.awaitItem()
      }

      // The alert should be dismissed
      awaitBody<MobilePayStatusModel> {
        switchCardModel.switchModel.checked.shouldBeTrue()
        disableAlertModel.shouldBeNull()
      }
    }
  }

  test("enabling -> dismiss disabling mobile pay with existing limit") {
    stateMachine.test(
      props(
        mobilePayData = MobilePayEnabledData(activeSpendingLimit = SpendingLimitMock)
      )
    ) {
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
    stateMachine.test(
      props(
        mobilePayData = MobilePayDisabledData(mostRecentSpendingLimit = SpendingLimitMock)
      )
    ) {
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
    stateMachine.test(
      props(
        mobilePayData = MobilePayDisabledData(mostRecentSpendingLimit = SpendingLimitMock),
        fiatCurrency = EUR
      )
    ) {
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
