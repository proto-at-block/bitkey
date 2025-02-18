package build.wallet.statemachine.limit.picker

import app.cash.turbine.plusAssign
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.Money
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryFake
import build.wallet.money.exchange.ExchangeRateServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.RetreatStyle.Close
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.money.calculator.MoneyCalculatorModel
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiProps
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class SpendingLimitPickerUiStateMachineImplTests : FunSpec({
  val defaultMoneyCalculatorModel =
    MoneyCalculatorModel(
      primaryAmount = FiatMoney.usd(0),
      secondaryAmount = BitcoinMoney.sats(0),
      amountModel =
        MoneyAmountEntryModel(
          primaryAmount = "$0",
          primaryAmountGhostedSubstringRange = null,
          secondaryAmount = "0 sats"
        ),
      keypadModel = KeypadModel(showDecimal = true, onButtonPress = {})
    )
  val moneyCalculatorUiStateMachine =
    object : MoneyCalculatorUiStateMachine,
      StateMachineMock<MoneyCalculatorUiProps, MoneyCalculatorModel>(
        defaultMoneyCalculatorModel
      ) {}
  val stateMachine: SpendingLimitPickerUiStateMachine =
    SpendingLimitPickerUiStateMachineImpl(
      exchangeRateService = ExchangeRateServiceFake(),
      proofOfPossessionNfcStateMachine =
        object : ProofOfPossessionNfcStateMachine, ScreenStateMachineMock<ProofOfPossessionNfcProps>(
          id = "pop-nfc"
        ) {},
      moneyCalculatorUiStateMachine = moneyCalculatorUiStateMachine,
      fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryFake()
    )

  val onCloseCalls = turbines.create<Unit>("close calls")
  val onSaveLimitCalls = turbines.create<Pair<Money, HwFactorProofOfPossession>>("save limit calls")
  val testMoney = FiatMoney.usd(100.0)

  val props =
    SpendingLimitPickerUiProps(
      account = FullAccountMock,
      initialLimit = FiatMoney.zeroUsd(),
      retreat = Retreat(style = Close, onRetreat = { onCloseCalls += Unit }),
      onSaveLimit = { value, _, hwPoP -> onSaveLimitCalls += Pair(value, hwPoP) }
    )

  val moneyCalculatorModelWithAmount = defaultMoneyCalculatorModel.copy(
    primaryAmount = testMoney,
    secondaryAmount = BitcoinMoney.sats(10000),
    amountModel = MoneyAmountEntryModel(
      primaryAmount = "$100",
      primaryAmountGhostedSubstringRange = null,
      secondaryAmount = "10,000 sats"
    )
  )

  test("initial state - zero limit") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<SpendingLimitPickerModel> {
        with(entryMode.shouldBeTypeOf<EntryMode.Keypad>().amountModel) {
          primaryAmount.shouldBe("$0")
          secondaryAmount.shouldBe("0 sats")
        }
        setLimitButtonModel.isEnabled.shouldBeFalse()
      }
    }
  }

  test("initial state - nonzero limit") {
    moneyCalculatorUiStateMachine.emitModel(moneyCalculatorModelWithAmount)

    stateMachine.testWithVirtualTime(props) {
      awaitBody<SpendingLimitPickerModel> {
        entryMode.shouldBeTypeOf<EntryMode.Keypad>()
        setLimitButtonModel.isEnabled.shouldBeTrue()
      }
    }
  }

  test("onClose prop is called for onBack") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<SpendingLimitPickerModel> {
        onBack()
      }
      onCloseCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("onClose prop is called for toolbar") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<SpendingLimitPickerModel> {
        toolbarModel.leadingAccessory.shouldBeTypeOf<IconAccessory>()
          .model.onClick.invoke()
      }
      onCloseCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("onTokenRefresh returns SpendingLimitPickerModel with loading button") {
    moneyCalculatorUiStateMachine.emitModel(moneyCalculatorModelWithAmount)
    stateMachine.testWithVirtualTime(props) {
      // initial state
      awaitBody<SpendingLimitPickerModel> {
        setLimitButtonModel.isLoading.shouldBeFalse()
        setLimitButtonModel.onClick()
      }

      // hw proof of possession
      awaitBodyMock<ProofOfPossessionNfcProps> {
        val model = onTokenRefresh.shouldNotBeNull().invoke()
        val limitBody = model.body.shouldBeInstanceOf<SpendingLimitPickerModel>()
        limitBody.setLimitButtonModel.isLoading.shouldBeTrue()
      }
    }
  }

  test("onTokenRefreshError returns SpendingLimitPickerModel with error sheet") {
    moneyCalculatorUiStateMachine.emitModel(moneyCalculatorModelWithAmount)
    stateMachine.testWithVirtualTime(props) {
      // initial state
      with(awaitItem()) {
        bottomSheetModel.shouldBeNull()
        val limitBody = body.shouldBeInstanceOf<SpendingLimitPickerModel>()
        limitBody.setLimitButtonModel.isLoading.shouldBeFalse()
        limitBody.setLimitButtonModel.onClick()
      }

      // hw proof of possession
      awaitBodyMock<ProofOfPossessionNfcProps> {
        val model = onTokenRefreshError.shouldNotBeNull().invoke(false) {}
        model.bottomSheetModel.shouldNotBeNull()
      }
    }
  }
})
