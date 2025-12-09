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
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.time.MinimumLoadingDuration
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
    val threshold = VerificationThreshold.Enabled(BitcoinMoney.btc(1.0))
    val policy = TxVerificationPolicy.Active(threshold)
    txVerificationService.updateThreshold(
      policy = policy,
      amountBtc = BitcoinMoney.btc(1.0),
      hwFactorProofOfPossession = HwFactorProofOfPossession("fake")
    )

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

  test("when updateThreshold returns pending, transitions to awaiting approval state") {
    txVerificationService.updateThresholdReturnsPending = true

    stateMachine.test(props) {
      awaitItem()

      awaitUntilBody<TxVerificationPolicyStateModel> {
        checked.shouldBe(false)
        updatePolicy(true)
      }

      awaitSheet<ChooseTxPolicyTypeSheetBody> {
        onAlwaysClick()
      }

      awaitBodyMock<ProofOfPossessionNfcProps> {
        val hwKeyProof = request.shouldBeInstanceOf<build.wallet.statemachine.auth.Request.HwKeyProof>()
        hwKeyProof.onSuccess(HwFactorProofOfPossession("test-proof"))
      }

      awaitBody<TxVerificationPolicyStateModel>()

      awaitUntilBody<build.wallet.statemachine.core.LoadingSuccessBodyModel>(
        id = build.wallet.analytics.events.screen.id.TxVerificationEventTrackerScreenId.APPROVE_LIMIT
      )
    }
  }

  test("clicking cancel on awaiting approval screen returns to overview") {
    txVerificationService.updateThresholdReturnsPending = true

    stateMachine.test(props) {
      awaitItem()

      awaitUntilBody<TxVerificationPolicyStateModel> {
        updatePolicy(true)
      }

      awaitSheet<ChooseTxPolicyTypeSheetBody> {
        onAlwaysClick()
      }

      awaitBodyMock<ProofOfPossessionNfcProps> {
        val hwKeyProof = request.shouldBeInstanceOf<build.wallet.statemachine.auth.Request.HwKeyProof>()
        hwKeyProof.onSuccess(HwFactorProofOfPossession("test-proof"))
      }

      awaitBody<TxVerificationPolicyStateModel>()

      awaitUntilBody<build.wallet.statemachine.core.LoadingSuccessBodyModel>(
        id = build.wallet.analytics.events.screen.id.TxVerificationEventTrackerScreenId.APPROVE_LIMIT
      ) {
        val cancelButton = secondaryButton.shouldBeInstanceOf<build.wallet.ui.model.button.ButtonModel>()
        cancelButton.onClick()
      }

      awaitBody<TxVerificationPolicyStateModel>()
    }
  }

  test("clicking resend email on awaiting approval screen returns to hardware confirmation") {
    txVerificationService.updateThresholdReturnsPending = true

    stateMachine.test(props) {
      awaitItem()

      awaitUntilBody<TxVerificationPolicyStateModel> {
        updatePolicy(true)
      }

      awaitSheet<ChooseTxPolicyTypeSheetBody> {
        onAlwaysClick()
      }

      awaitBodyMock<ProofOfPossessionNfcProps> {
        val hwKeyProof = request.shouldBeInstanceOf<build.wallet.statemachine.auth.Request.HwKeyProof>()
        hwKeyProof.onSuccess(HwFactorProofOfPossession("test-proof"))
      }

      awaitBody<TxVerificationPolicyStateModel>()

      awaitUntilBody<build.wallet.statemachine.core.LoadingSuccessBodyModel>(
        id = build.wallet.analytics.events.screen.id.TxVerificationEventTrackerScreenId.APPROVE_LIMIT
      ) {
        val resendButton = primaryButton.shouldBeInstanceOf<build.wallet.ui.model.button.ButtonModel>()
        resendButton.onClick()
      }

      awaitBodyMock<ProofOfPossessionNfcProps>()
    }
  }

  test("when updateThreshold returns an error, transitions to error state") {
    txVerificationService.updateThresholdReturnsError = true

    stateMachine.test(props) {
      awaitItem()

      awaitUntilBody<TxVerificationPolicyStateModel> {
        updatePolicy(true)
      }

      awaitSheet<ChooseTxPolicyTypeSheetBody> {
        onAlwaysClick()
      }

      awaitBodyMock<ProofOfPossessionNfcProps> {
        val hwKeyProof = request.shouldBeInstanceOf<build.wallet.statemachine.auth.Request.HwKeyProof>()
        hwKeyProof.onSuccess(HwFactorProofOfPossession("test-proof"))
      }

      awaitBody<TxVerificationPolicyStateModel>()

      awaitUntilBody<build.wallet.statemachine.core.form.FormBodyModel>(
        id = build.wallet.analytics.events.screen.id.TxVerificationEventTrackerScreenId.POLICY_UPDATE_FAILURE
      )
    }
  }

  test("clicking cancel on error screen returns to overview") {
    txVerificationService.updateThresholdReturnsError = true

    stateMachine.test(props) {
      awaitItem()

      awaitUntilBody<TxVerificationPolicyStateModel> {
        updatePolicy(true)
      }

      awaitSheet<ChooseTxPolicyTypeSheetBody> {
        onAlwaysClick()
      }

      awaitBodyMock<ProofOfPossessionNfcProps> {
        val hwKeyProof = request.shouldBeInstanceOf<build.wallet.statemachine.auth.Request.HwKeyProof>()
        hwKeyProof.onSuccess(HwFactorProofOfPossession("test-proof"))
      }

      awaitBody<TxVerificationPolicyStateModel>()

      awaitUntilBody<build.wallet.statemachine.core.form.FormBodyModel>(
        id = build.wallet.analytics.events.screen.id.TxVerificationEventTrackerScreenId.POLICY_UPDATE_FAILURE
      ) {
        val cancelButton = secondaryButton.shouldBeInstanceOf<build.wallet.ui.model.button.ButtonModel>()
        cancelButton.onClick()
      }

      awaitBody<TxVerificationPolicyStateModel>()
    }
  }

  test("clicking retry on error screen retries the update") {
    txVerificationService.updateThresholdReturnsError = true

    stateMachine.test(props) {
      awaitItem()

      awaitUntilBody<TxVerificationPolicyStateModel> {
        updatePolicy(true)
      }

      awaitSheet<ChooseTxPolicyTypeSheetBody> {
        onAlwaysClick()
      }

      awaitBodyMock<ProofOfPossessionNfcProps> {
        val hwKeyProof = request.shouldBeInstanceOf<build.wallet.statemachine.auth.Request.HwKeyProof>()
        hwKeyProof.onSuccess(HwFactorProofOfPossession("test-proof"))
      }

      awaitBody<TxVerificationPolicyStateModel>()

      awaitUntilBody<build.wallet.statemachine.core.form.FormBodyModel>(
        id = build.wallet.analytics.events.screen.id.TxVerificationEventTrackerScreenId.POLICY_UPDATE_FAILURE
      ) {
        txVerificationService.updateThresholdReturnsError = false
        val retryButton = primaryButton.shouldBeInstanceOf<build.wallet.ui.model.button.ButtonModel>()
        retryButton.onClick()
      }

      awaitBody<TxVerificationPolicyStateModel>()

      awaitBody<TxVerificationPolicyStateModel> {
        checked.shouldBe(true)
      }
    }
  }
})
