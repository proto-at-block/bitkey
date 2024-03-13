package build.wallet.statemachine.limit

import app.cash.turbine.plusAssign
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.limit.SpendingLimit
import build.wallet.limit.SpendingLimitMock
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.USD
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.data.mobilepay.MobilePayDisabledDataMock
import build.wallet.statemachine.data.mobilepay.MobilePayEnabledDataMock
import build.wallet.statemachine.limit.picker.SpendingLimitPickerUiProps
import build.wallet.statemachine.limit.picker.SpendingLimitPickerUiStateMachine
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.time.TimeZoneProviderMock
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.TimeZone

class SpendingLimitUiStateMachineImplTests : FunSpec({
  val onCloseCalls = turbines.create<Unit>("close calls")
  val onSetLimitCalls = turbines.create<SpendingLimit>("set limit calls")

  val timeZoneProvider = TimeZoneProviderMock()

  val stateMachine: SetSpendingLimitUiStateMachine =
    SetSpendingLimitUiStateMachineImpl(
      spendingLimitPickerUiStateMachine =
        object : SpendingLimitPickerUiStateMachine, ScreenStateMachineMock<SpendingLimitPickerUiProps>(
          id = "spending-limit-picker"
        ) {},
      timeZoneProvider = timeZoneProvider,
      moneyDisplayFormatter = MoneyDisplayFormatterFake
    )

  val props =
    SpendingLimitProps(
      currentSpendingLimit = null,
      onClose = { onCloseCalls += Unit },
      onSetLimit = { onSetLimitCalls += it },
      accountData = ActiveKeyboxLoadedDataMock,
      fiatCurrency = USD
    )

  val testSpendingLimit = FiatMoney.usd(100.0)

  test("initial state with no spending limit") {
    val testProps =
      props.copy(
        accountData =
          ActiveKeyboxLoadedDataMock.copy(
            mobilePayData = MobilePayDisabledDataMock
          )
      )

    stateMachine.test(testProps) {
      awaitScreenWithBodyModelMock<SpendingLimitPickerUiProps> {
        initialLimit.shouldBe(FiatMoney.zero(USD))
      }
    }
  }

  test("initial state with spending limit") {
    val testProps =
      props.copy(
        currentSpendingLimit = SpendingLimitMock(testSpendingLimit).amount,
        accountData =
          ActiveKeyboxLoadedDataMock.copy(
            mobilePayData =
              MobilePayEnabledDataMock.copy(
                activeSpendingLimit = SpendingLimitMock(testSpendingLimit)
              )
          )
      )

    stateMachine.test(testProps) {
      awaitScreenWithBodyModelMock<SpendingLimitPickerUiProps> {
        initialLimit.shouldBe(testSpendingLimit)
      }
    }
  }

  test("onSaveLimit leads to saving limit loading screen") {
    val testProps =
      props.copy(
        accountData =
          ActiveKeyboxLoadedDataMock.copy(
            mobilePayData =
              MobilePayEnabledDataMock.copy(
                changeSpendingLimit = { _, _, _, onResult ->
                  onResult(Ok(Unit))
                }
              )
          )
      )
    val limit = SpendingLimit(active = true, FiatMoney.usd(100.0), TimeZone.UTC)

    stateMachine.test(testProps) {
      awaitScreenWithBodyModelMock<SpendingLimitPickerUiProps> {
        onSaveLimit(
          FiatMoney.usd(100.0),
          BitcoinMoney.btc(1.0),
          HwFactorProofOfPossession("")
        )
      }
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        message.shouldNotBeNull().shouldBe("Saving Limit...")
      }
      awaitScreenWithBody<FormBodyModel> {
        header.shouldNotBeNull().run {
          headline.shouldBe("You're all set.")
          sublineModel.shouldNotBeNull().string.shouldBe(
            "Now you can spend up to $100.00 (100,000,000 sats) per day with just your phone."
          )
        }
        clickPrimaryButton()
      }

      onSetLimitCalls.awaitItem().shouldBe(limit)
    }
  }
})
