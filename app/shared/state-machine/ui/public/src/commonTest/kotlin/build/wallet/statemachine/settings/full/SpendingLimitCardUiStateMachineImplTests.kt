package build.wallet.statemachine.settings.full

import build.wallet.limit.SpendingLimitMock
import build.wallet.money.FiatMoney
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.platform.settings.LocaleIdentifierProviderFake
import build.wallet.statemachine.core.test
import build.wallet.statemachine.settings.full.mobilepay.SpendingLimitCardUiProps
import build.wallet.statemachine.settings.full.mobilepay.SpendingLimitCardUiStateMachineImpl
import build.wallet.time.TimeZoneFormatterMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SpendingLimitCardUiStateMachineImplTests : FunSpec({
  val stateMachine =
    SpendingLimitCardUiStateMachineImpl(
      moneyDisplayFormatter = MoneyDisplayFormatterFake,
      timeZoneFormatter = TimeZoneFormatterMock(),
      localeIdentifierProvider = LocaleIdentifierProviderFake()
    )

  val props =
    SpendingLimitCardUiProps(
      spendingLimit = SpendingLimitMock,
      remainingAmount = FiatMoney.zeroUsd()
    )

  test("spending limit card with 0 remaining amount") {
    stateMachine.test(props) {
      with(awaitItem()) {
        titleText.shouldBe("Today’s limit")
        dailyResetTimezoneText.shouldBe("Resets at 3:00am PDT")
        spentAmountText.shouldBe("$1.00 spent")
        remainingAmountText.shouldBe("$0.00 remaining")
        progressPercentage.shouldBe(1.0f)
      }
    }
  }

  test("spending limit card with $1.00 remaining amount") {
    stateMachine.test(
      props.copy(remainingAmount = FiatMoney.usd(100))
    ) {
      with(awaitItem()) {
        titleText.shouldBe("Today’s limit")
        dailyResetTimezoneText.shouldBe("Resets at 3:00am PDT")
        spentAmountText.shouldBe("$0.00 spent")
        remainingAmountText.shouldBe("$1.00 remaining")
        progressPercentage.shouldBe(0.0f)
      }
    }
  }

  test("spending limit card with $0.50 remaining amount") {
    stateMachine.test(
      props.copy(remainingAmount = FiatMoney.usd(50))
    ) {
      with(awaitItem()) {
        titleText.shouldBe("Today’s limit")
        dailyResetTimezoneText.shouldBe("Resets at 3:00am PDT")
        spentAmountText.shouldBe("$0.50 spent")
        remainingAmountText.shouldBe("$0.50 remaining")
        progressPercentage.shouldBe(0.5f)
      }
    }
  }

  test("spending limit card with $0.33 remaining amount") {
    stateMachine.test(
      props.copy(remainingAmount = FiatMoney.usd(33))
    ) {
      with(awaitItem()) {
        titleText.shouldBe("Today’s limit")
        dailyResetTimezoneText.shouldBe("Resets at 3:00am PDT")
        spentAmountText.shouldBe("$0.67 spent")
        remainingAmountText.shouldBe("$0.33 remaining")
        progressPercentage.shouldBe(0.67f)
      }
    }
  }
})
