package bitkey.ui.verification

import bitkey.verification.TxVerificationPolicy
import bitkey.verification.TxVerificationServiceFake
import bitkey.verification.VerificationThreshold
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryFake
import build.wallet.money.exchange.ExchangeRateServiceFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.core.test
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.money.calculator.MoneyCalculatorModel
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiProps
import build.wallet.statemachine.money.calculator.MoneyCalculatorUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.time.MinimumLoadingDuration
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds

class TxVerificationPolicyStateMachineImplTests : FunSpec({

  val txVerificationService = TxVerificationServiceFake()
  val formatter = MoneyDisplayFormatterFake
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryFake()
  val exchangeRateService = ExchangeRateServiceFake()
  val minimumLoadingDuration = MinimumLoadingDuration(0.milliseconds)

  val defaultMoneyCalculatorModel = MoneyCalculatorModel(
    primaryAmount = FiatMoney.usd(0),
    secondaryAmount = BitcoinMoney.sats(0),
    amountModel = MoneyAmountEntryModel(
      primaryAmount = "$0",
      primaryAmountGhostedSubstringRange = null,
      secondaryAmount = "0 sats"
    ),
    keypadModel = KeypadModel(showDecimal = true, onButtonPress = {})
  )

  val moneyInputStateMachine = object : MoneyCalculatorUiStateMachine,
    StateMachineMock<MoneyCalculatorUiProps, MoneyCalculatorModel>(
      defaultMoneyCalculatorModel
    ) {}

  val hwProofOfPossessionNfcStateMachine = object : ProofOfPossessionNfcStateMachine,
    ScreenStateMachineMock<ProofOfPossessionNfcProps>(
      id = "proof-of-possession-nfc"
    ) {}

  val stateMachine = TxVerificationPolicyStateMachineImpl(
    txVerificationService = txVerificationService,
    formatter = formatter,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    moneyInputStateMachine = moneyInputStateMachine,
    exchangeRateService = exchangeRateService,
    minimumLoadingDuration = minimumLoadingDuration,
    hwProofOfPossessionNfcStateMachine = hwProofOfPossessionNfcStateMachine
  )

  val onExitCalls = turbines.create<Unit>("onExit calls")
  val props = TxVerificationPolicyProps(
    onExit = { onExitCalls.add(Unit) },
    account = FullAccountMock
  )

  beforeEach {
    txVerificationService.reset()
    fiatCurrencyPreferenceRepository.reset()
    exchangeRateService.reset()
  }

  test("overview state shows disabled verification when no threshold is set") {
    stateMachine.test(props) {
      awaitItem() // Loading state
      awaitBody<TxVerificationPolicyStateModel> {
        checked.shouldBeFalse()
        enabled.shouldBeTrue()
      }
    }
  }

  test("overview state shows enabled verification when threshold is set") {
    // Set up service to return a threshold (enabled)
    val threshold = VerificationThreshold(BitcoinMoney.btc(1.0))
    val policy = TxVerificationPolicy.Active(threshold)
    txVerificationService.updateThreshold(policy, HwFactorProofOfPossession("fake"))

    stateMachine.test(props) {
      awaitItem() // Loading state
      awaitBody<TxVerificationPolicyStateModel> {
        checked.shouldBe(true)
        enabled.shouldBe(true)
      }
    }
  }

  test("onExit callback is called from overview state") {
    stateMachine.test(props) {
      awaitItem() // Loading state

      awaitBody<TxVerificationPolicyStateModel> {
        onBack()
      }

      onExitCalls.awaitItem()
    }
  }

  test("Setting a new threshold above a certain amount from disabled state") {
    stateMachine.test(props) {
      awaitItem() // Loading state

      awaitBody<TxVerificationPolicyStateModel> {
        checked.shouldBe(false)
        enabled.shouldBe(true)
        updatePolicy(true)
      }

      awaitSheet<ChooseTxPolicyTypeSheetBody> {
        onAboveAmountClick()
      }

      awaitSheet<VerificationThresholdPickerModel> {
        onConfirmClick()
      }
    }
  }

  test("Setting a new threshold always from disabled state") {
    stateMachine.test(props) {
      awaitItem() // Loading state

      awaitBody<TxVerificationPolicyStateModel> {
        checked.shouldBe(false)
        enabled.shouldBe(true)
        updatePolicy(true)
      }

      awaitSheet<ChooseTxPolicyTypeSheetBody> {
        onAlwaysClick()
      }
    }
  }
})
