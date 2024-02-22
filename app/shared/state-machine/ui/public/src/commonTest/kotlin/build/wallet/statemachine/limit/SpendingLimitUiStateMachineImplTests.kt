package build.wallet.statemachine.limit

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_MOBILE_TRANSACTION_SKIP
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTask.TaskId.EnableSpendingLimit
import build.wallet.home.GettingStartedTask.TaskState.Complete
import build.wallet.home.GettingStartedTask.TaskState.Incomplete
import build.wallet.home.GettingStartedTaskDaoMock
import build.wallet.limit.SpendingLimit
import build.wallet.limit.SpendingLimitMock
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.USD
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.SuccessBodyModel.Style.Explicit
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.data.mobilepay.MobilePayDisabledDataMock
import build.wallet.statemachine.data.mobilepay.MobilePayEnabledDataMock
import build.wallet.statemachine.limit.SpendingLimitEntryPoint.GettingStarted
import build.wallet.statemachine.limit.SpendingLimitEntryPoint.Settings
import build.wallet.statemachine.limit.picker.SpendingLimitPickerUiProps
import build.wallet.statemachine.limit.picker.SpendingLimitPickerUiStateMachine
import build.wallet.time.TimeZoneProviderMock
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.TimeZone

class SpendingLimitUiStateMachineImplTests : FunSpec({
  val onCloseCalls = turbines.create<Unit>("close calls")
  val onSetLimitCalls = turbines.create<SpendingLimit>("set limit calls")

  val eventTracker = EventTrackerMock(turbines::create)
  val timeZoneProvider = TimeZoneProviderMock()

  val gettingStartedTaskDao =
    GettingStartedTaskDaoMock(
      turbine = turbines::create,
      initialTasks = listOf(GettingStartedTask(EnableSpendingLimit, Incomplete))
    )
  val stateMachine: SetSpendingLimitUiStateMachine =
    SetSpendingLimitUiStateMachineImpl(
      spendingLimitPickerUiStateMachine =
        object : SpendingLimitPickerUiStateMachine, ScreenStateMachineMock<SpendingLimitPickerUiProps>(
          id = "spending-limit-picker"
        ) {},
      eventTracker = eventTracker,
      timeZoneProvider = timeZoneProvider,
      gettingStartedTaskDao = gettingStartedTaskDao,
      moneyDisplayFormatter = MoneyDisplayFormatterFake
    )

  val props =
    SpendingLimitProps(
      currentSpendingLimit = null,
      onClose = { onCloseCalls += Unit },
      onSetLimit = { onSetLimitCalls += it },
      accountData = ActiveKeyboxLoadedDataMock,
      entryPoint = Settings,
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
      awaitScreenWithBody<LoadingBodyModel> {
        message.shouldBe("Saving Limit...")
      }
      awaitScreenWithBody<SuccessBodyModel> {
        title.shouldBe("That’s it!")
        message.shouldBe(
          "Now you can spend up to $100.00 (100,000,000 sats) per day with just your phone."
        )
        style.shouldBeInstanceOf<Explicit>().primaryButton.onClick()
      }

      onSetLimitCalls.awaitItem().shouldBe(limit)
    }
  }

  test("onRetreat calls the passed in onClose prop when entry is Settings") {
    stateMachine.test(props.copy(entryPoint = Settings)) {
      awaitScreenWithBodyModelMock<SpendingLimitPickerUiProps> {
        retreat.onRetreat()
      }
      onCloseCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("onRetreat goes back when entry is GettingStarted") {
    stateMachine.test(props.copy(entryPoint = GettingStarted)) {
      awaitScreenWithBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitScreenWithBodyModelMock<SpendingLimitPickerUiProps> {
        retreat.onRetreat()
      }
      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("Getting Started entry point goes to enable transactions screen") {
    stateMachine.test(props.copy(entryPoint = GettingStarted)) {
      awaitScreenWithBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Enable mobile pay")
        header.shouldNotBeNull().sublineModel.shouldNotBeNull().string.shouldBe(
          "Leave your device at home, and make small spends with just the key on your phone."
        )
        primaryButton.shouldNotBeNull().text.shouldBe("Continue")
        secondaryButton.shouldNotBeNull().text.shouldBe("Set up later")
      }
    }
  }

  test("Enable transactions screen goes to picker on continue") {
    stateMachine.test(props.copy(entryPoint = GettingStarted)) {
      awaitScreenWithBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }

      awaitScreenWithBodyModelMock<SpendingLimitPickerUiProps>()
    }
  }

  test("Enable transactions screen closes statemachine on setup later") {
    stateMachine.test(props.copy(entryPoint = GettingStarted)) {
      awaitScreenWithBody<FormBodyModel> {
        secondaryButton!!.onClick()
      }

      awaitScreenWithBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isEnabled.shouldBeFalse()
        with(secondaryButton.shouldNotBeNull()) {
          isEnabled.shouldBeFalse()
          isLoading.shouldBeTrue()
        }
      }

      gettingStartedTaskDao.gettingStartedTasks.value.shouldBe(
        listOf(GettingStartedTask(EnableSpendingLimit, Complete))
      )

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_MOBILE_TRANSACTION_SKIP)
      )
      onCloseCalls.awaitItem().shouldBe(Unit)
    }
  }
})
