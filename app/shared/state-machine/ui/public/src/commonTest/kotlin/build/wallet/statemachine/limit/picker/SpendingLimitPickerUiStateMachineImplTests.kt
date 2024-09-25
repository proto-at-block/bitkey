package build.wallet.statemachine.limit.picker

import app.cash.turbine.plusAssign
import build.wallet.configuration.MobilePayFiatConfigServiceFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.MobilePayRevampFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.Money
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryFake
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.exchange.ExchangeRateServiceFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.RetreatStyle.Close
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.money.calculator.MoneyCalculatorModel
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiProps
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachine
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class SpendingLimitPickerUiStateMachineImplTests : FunSpec({
  val mobilePayFiatConfigService = MobilePayFiatConfigServiceFake()
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
  val mobilePayRevampFeatureFlag = MobilePayRevampFeatureFlag(featureFlagDao = FeatureFlagDaoFake())
  val stateMachine: SpendingLimitPickerUiStateMachine =
    SpendingLimitPickerUiStateMachineImpl(
      currencyConverter = CurrencyConverterFake(),
      mobilePayFiatConfigService = mobilePayFiatConfigService,
      exchangeRateService = ExchangeRateServiceFake(),
      moneyDisplayFormatter = MoneyDisplayFormatterFake,
      proofOfPossessionNfcStateMachine =
        object : ProofOfPossessionNfcStateMachine, ScreenStateMachineMock<ProofOfPossessionNfcProps>(
          id = "pop-nfc"
        ) {},
      moneyCalculatorUiStateMachine = moneyCalculatorUiStateMachine,
      fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryFake(),
      mobilePayRevampFeatureFlag = mobilePayRevampFeatureFlag
    )

  val onCloseCalls = turbines.create<Unit>("close calls")
  val onSaveLimitCalls = turbines.create<Pair<Money, HwFactorProofOfPossession>>("save limit calls")
  val testMoney = FiatMoney.usd(100.0)

  val props =
    SpendingLimitPickerUiProps(
      accountData = ActiveKeyboxLoadedDataMock,
      initialLimit = FiatMoney.zeroUsd(),
      retreat = Retreat(style = Close, onRetreat = { onCloseCalls += Unit }),
      onSaveLimit = { value, _, hwPoP -> onSaveLimitCalls += Pair(value, hwPoP) }
    )

  beforeTest {
    mobilePayFiatConfigService.reset()
    mobilePayRevampFeatureFlag.reset()
  }

  test("initial state - 0 limit") {
    stateMachine.test(props) {
      awaitScreenWithBody<SpendingLimitPickerModel> {
        with(entryMode.shouldBeTypeOf<EntryMode.Slider>().sliderModel) {
          value.shouldBe(0f)
          primaryAmount.shouldBe("$0")
          secondaryAmount.shouldBe("0 sats")
          valueRange.start.shouldBe(0f)
          valueRange.endInclusive.shouldBe(200f)
        }
        setLimitButtonModel.isEnabled.shouldBeFalse()
      }
    }
  }

  test("initial state - 100 limit") {
    stateMachine.test(
      props.copy(initialLimit = testMoney)
    ) {
      awaitScreenWithBody<SpendingLimitPickerModel>() // Initial loading
      awaitScreenWithBody<SpendingLimitPickerModel> {

        with(entryMode.shouldBeTypeOf<EntryMode.Slider>().sliderModel) {
          value.shouldBe(100f)
          primaryAmount.shouldBe("$100")
          // fake currency converter always triples value
          secondaryAmount.shouldBe("30,000,000,000 sats")
          valueRange.start.shouldBe(0f)
          valueRange.endInclusive.shouldBe(200f)
        }
        setLimitButtonModel.isEnabled.shouldBeTrue()
      }
    }
  }

  test("tolerance range for key value snaps appropriately") {
    stateMachine.test(props) {
      // initial state
      awaitScreenWithBody<SpendingLimitPickerModel> {
        with(entryMode.shouldBeTypeOf<EntryMode.Slider>().sliderModel) {
          value.shouldBe(0f)
          primaryAmount.shouldBe("$0")
          secondaryAmount.shouldBe("0 sats")
          valueRange.start.shouldBe(0f)
          valueRange.endInclusive.shouldBe(200f)
          onValueUpdate(174f)
        }
        setLimitButtonModel.isEnabled.shouldBeFalse()
      }

      // Loading
      awaitScreenWithBody<SpendingLimitPickerModel>()

      // updated value
      awaitScreenWithBody<SpendingLimitPickerModel> {
        with(entryMode.shouldBeTypeOf<EntryMode.Slider>().sliderModel) {
          value.shouldBe(175f)
          primaryAmount.shouldBe("$175")
          secondaryAmount.shouldBe("52,500,000,000 sats")
        }
      }
    }
  }

  test("onClose prop is called for onBack") {
    stateMachine.test(props) {
      awaitScreenWithBody<SpendingLimitPickerModel> {
        onBack()
      }
      onCloseCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("onClose prop is called for toolbar") {
    stateMachine.test(props) {
      awaitScreenWithBody<SpendingLimitPickerModel> {
        toolbarModel.leadingAccessory.shouldBeTypeOf<IconAccessory>()
          .model.onClick.invoke()
      }
      onCloseCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("onSaveLimit is called with initial limit when no change is made") {
    stateMachine.test(props.copy(initialLimit = testMoney)) {
      // Initial loading
      awaitScreenWithBody<SpendingLimitPickerModel>()

      // initial state
      awaitScreenWithBody<SpendingLimitPickerModel> {
        setLimitButtonModel.onClick()
      }

      // hw proof of possession
      val proof = HwFactorProofOfPossession("some-fake-token")
      awaitScreenWithBodyModelMock<ProofOfPossessionNfcProps> {
        (request as Request.HwKeyProof).onSuccess(proof)
      }
      onSaveLimitCalls.awaitItem().shouldBe(
        Pair(testMoney, proof)
      )
    }
  }

  test("onSaveLimit is called with changed limit") {
    stateMachine.test(props.copy(initialLimit = testMoney)) {
      // Initial loading
      awaitScreenWithBody<SpendingLimitPickerModel>()

      // initial state
      awaitScreenWithBody<SpendingLimitPickerModel> {
        val limitSliderModel = entryMode.shouldBeTypeOf<EntryMode.Slider>().sliderModel
        limitSliderModel.onValueUpdate(200f)
      }
      // Loading model for converted amount
      awaitScreenWithBody<SpendingLimitPickerModel>()

      // update value
      awaitScreenWithBody<SpendingLimitPickerModel> {
        with(entryMode.shouldBeTypeOf<EntryMode.Slider>().sliderModel) {
          value.shouldBe(200f)
          primaryAmount.shouldBe("$200")
          secondaryAmount.shouldBe("60,000,000,000 sats")
          valueRange.start.shouldBe(0f)
          valueRange.endInclusive.shouldBe(200f)
        }
        setLimitButtonModel.isLoading.shouldBeFalse()
        setLimitButtonModel.onClick()
      }
      // hw proof of possession
      val proof = HwFactorProofOfPossession("some-fake-token")
      awaitScreenWithBodyModelMock<ProofOfPossessionNfcProps> {
        (request as Request.HwKeyProof).onSuccess(proof)
      }
      onSaveLimitCalls.awaitItem().shouldBe(
        Pair(testMoney.copy(value = 200.toBigDecimal()), proof)
      )
    }
  }

  test("decimal values are rounded and saved as whole numbers") {
    stateMachine.test(props) {
      // initial state
      awaitScreenWithBody<SpendingLimitPickerModel> {
        val limitSliderModel = entryMode.shouldBeTypeOf<EntryMode.Slider>().sliderModel
        limitSliderModel.onValueUpdate(99.9f)
      }
      // Loading model for converted amount
      awaitScreenWithBody<SpendingLimitPickerModel>()
      awaitScreenWithBody<SpendingLimitPickerModel> {
        val limitSliderModel = entryMode.shouldBeTypeOf<EntryMode.Slider>().sliderModel
        limitSliderModel.value.shouldBe(100f)
      }
    }
  }

  test("onTokenRefresh returns SpendingLimitPickerModel with loading button") {
    stateMachine.test(props.copy(initialLimit = testMoney)) {
      // Initial loading
      awaitScreenWithBody<SpendingLimitPickerModel>()

      // initial state
      awaitScreenWithBody<SpendingLimitPickerModel> {
        setLimitButtonModel.isLoading.shouldBeFalse()
        setLimitButtonModel.onClick()
      }

      // hw proof of possession
      awaitScreenWithBodyModelMock<ProofOfPossessionNfcProps> {
        val model = onTokenRefresh.shouldNotBeNull().invoke()
        val limitBody = model.body.shouldBeInstanceOf<SpendingLimitPickerModel>()
        limitBody.setLimitButtonModel.isLoading.shouldBeTrue()
      }
    }
  }

  test("onTokenRefreshError returns SpendingLimitPickerModel with error sheet") {
    stateMachine.test(props.copy(initialLimit = testMoney)) {
      // Initial loading
      awaitScreenWithBody<SpendingLimitPickerModel>()

      // initial state
      with(awaitItem()) {
        bottomSheetModel.shouldBeNull()
        val limitBody = body.shouldBeInstanceOf<SpendingLimitPickerModel>()
        limitBody.setLimitButtonModel.isLoading.shouldBeFalse()
        limitBody.setLimitButtonModel.onClick()
      }

      // hw proof of possession
      awaitScreenWithBodyModelMock<ProofOfPossessionNfcProps> {
        val model = onTokenRefreshError.shouldNotBeNull().invoke(false) {}
        model.bottomSheetModel.shouldNotBeNull()
      }
    }
  }

  // When removing the feature flag, flatten this context and delete everything above.
  context("mobile pay revamp feature flag is on") {
    beforeTest {
      mobilePayRevampFeatureFlag.setFlagValue(true)
      moneyCalculatorUiStateMachine.reset()
    }

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
      stateMachine.test(props) {
        awaitScreenWithBody<SpendingLimitPickerModel> {
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

      stateMachine.test(props) {
        awaitScreenWithBody<SpendingLimitPickerModel> {
          entryMode.shouldBeTypeOf<EntryMode.Keypad>()
          setLimitButtonModel.isEnabled.shouldBeTrue()
        }
      }
    }

    test("onClose prop is called for onBack") {
      stateMachine.test(props) {
        awaitScreenWithBody<SpendingLimitPickerModel> {
          onBack()
        }
        onCloseCalls.awaitItem().shouldBe(Unit)
      }
    }

    test("onClose prop is called for toolbar") {
      stateMachine.test(props) {
        awaitScreenWithBody<SpendingLimitPickerModel> {
          toolbarModel.leadingAccessory.shouldBeTypeOf<IconAccessory>()
            .model.onClick.invoke()
        }
        onCloseCalls.awaitItem().shouldBe(Unit)
      }
    }

    test("onTokenRefresh returns SpendingLimitPickerModel with loading button") {
      moneyCalculatorUiStateMachine.emitModel(moneyCalculatorModelWithAmount)
      stateMachine.test(props) {
        // initial state
        awaitScreenWithBody<SpendingLimitPickerModel> {
          setLimitButtonModel.isLoading.shouldBeFalse()
          setLimitButtonModel.onClick()
        }

        // hw proof of possession
        awaitScreenWithBodyModelMock<ProofOfPossessionNfcProps> {
          val model = onTokenRefresh.shouldNotBeNull().invoke()
          val limitBody = model.body.shouldBeInstanceOf<SpendingLimitPickerModel>()
          limitBody.setLimitButtonModel.isLoading.shouldBeTrue()
        }
      }
    }

    test("onTokenRefreshError returns SpendingLimitPickerModel with error sheet") {
      moneyCalculatorUiStateMachine.emitModel(moneyCalculatorModelWithAmount)
      stateMachine.test(props) {
        // initial state
        with(awaitItem()) {
          bottomSheetModel.shouldBeNull()
          val limitBody = body.shouldBeInstanceOf<SpendingLimitPickerModel>()
          limitBody.setLimitButtonModel.isLoading.shouldBeFalse()
          limitBody.setLimitButtonModel.onClick()
        }

        // hw proof of possession
        awaitScreenWithBodyModelMock<ProofOfPossessionNfcProps> {
          val model = onTokenRefreshError.shouldNotBeNull().invoke(false) {}
          model.bottomSheetModel.shouldNotBeNull()
        }
      }
    }
  }
})
