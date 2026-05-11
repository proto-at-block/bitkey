package build.wallet.statemachine.settings.full

import build.wallet.limit.SpendingLimitMock
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.formatter.MoneyDisplayFormatterFake
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
      timeZoneFormatter = TimeZoneFormatterMock()
    )

  // SpendingLimitMock is $1.00. Use 1_000_000 sats as the equivalent for bitcoin-side math so the
  // sats progress ratio matches the cents ratio in each scenario.
  val limitSats = 1_000_000L

  fun props(
    spentCents: Long,
    remainingCents: Long = 100 - spentCents,
    spentSats: Long = spentCents * limitSats / 100,
    remainingSats: Long = limitSats - spentSats,
  ) = SpendingLimitCardUiProps(
    spendingLimit = SpendingLimitMock,
    spentAmount = FiatMoney.usd(spentCents),
    remainingAmount = FiatMoney.usd(remainingCents),
    spentBitcoinAmount = BitcoinMoney.sats(spentSats),
    remainingBitcoinAmount = BitcoinMoney.sats(remainingSats)
  )

  test("spending limit card with 0 remaining amount") {
    stateMachine.test(props(spentCents = 100)) {
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
    stateMachine.test(props(spentCents = 0)) {
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
    stateMachine.test(props(spentCents = 50)) {
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
    stateMachine.test(props(spentCents = 67, remainingCents = 33)) {
      with(awaitItem()) {
        titleText.shouldBe("Today’s limit")
        dailyResetTimezoneText.shouldBe("Resets at 3:00am PDT")
        spentAmountText.shouldBe("$0.67 spent")
        remainingAmountText.shouldBe("$0.33 remaining")
        progressPercentage.shouldBe(0.67f)
      }
    }
  }

  test("renders spentAmount directly without subtracting from limit") {
    stateMachine.test(props(spentCents = 25, remainingCents = 75, spentSats = 250_000, remainingSats = 750_000)) {
      with(awaitItem()) {
        spentAmountText.shouldBe("$0.25 spent")
        remainingAmountText.shouldBe("$0.75 remaining")
        progressPercentage.shouldBe(0.25f)
      }
    }
  }

  test("progress is 1.0 when available sats = 0") {
    stateMachine.test(
      props(spentCents = 100, spentSats = limitSats, remainingSats = 0)
    ) {
      awaitItem().progressPercentage.shouldBe(1.0f)
    }
  }

  test("progress is 0.0 when both sats are 0 (no div-by-zero)") {
    stateMachine.test(
      props(spentCents = 0, remainingCents = 0, spentSats = 0, remainingSats = 0)
    ) {
      awaitItem().progressPercentage.shouldBe(0.0f)
    }
  }
})
