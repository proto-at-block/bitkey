package build.wallet.statemachine.sweep

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.screen.id.AppRecoveryEventTrackerScreenId
import build.wallet.bdk.bindings.BdkError.Generic
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.KeyboxConfigMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock2
import build.wallet.compose.collections.immutableListOf
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.currency.USD
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.recovery.sweep.SweepGenerator.SweepGeneratorError.FailedToListKeysets
import build.wallet.recovery.sweep.SweepPsbt
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.sweep.SweepData.AwaitingHardwareSignedSweepsData
import build.wallet.statemachine.data.recovery.sweep.SweepData.GeneratePsbtsFailedData
import build.wallet.statemachine.data.recovery.sweep.SweepData.GeneratingPsbtsData
import build.wallet.statemachine.data.recovery.sweep.SweepData.NoFundsFoundData
import build.wallet.statemachine.data.recovery.sweep.SweepData.PsbtsGeneratedData
import build.wallet.statemachine.data.recovery.sweep.SweepData.SigningAndBroadcastingSweepsData
import build.wallet.statemachine.data.recovery.sweep.SweepData.SweepCompleteData
import build.wallet.statemachine.data.recovery.sweep.SweepData.SweepFailedData
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.statemachine.money.amount.MoneyAmountUiProps
import build.wallet.statemachine.money.amount.MoneyAmountUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachineImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableMap

class SweepUiStateMachineImplTests : FunSpec({
  val onExitCalls = turbines.create<Unit>("exit calls")
  val onExitCallback = { onExitCalls += Unit }
  val onRetryCalls = turbines.create<Unit>("retry calls")
  val onRetryCallback = { onRetryCalls += Unit }
  val startSweepCalls = turbines.create<Unit>("start sweep calls")
  val addHwSignedSweepsCalls =
    turbines.create<List<Psbt>>("add hw signed psbts calls")

  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine, ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
      "nfc"
    ) {}
  val moneyAmountUiStateMachine =
    object : MoneyAmountUiStateMachine,
      StateMachineMock<MoneyAmountUiProps, MoneyAmountModel>(
        MoneyAmountModel(
          primaryAmount = "10,000 sats",
          secondaryAmount = "$100.00"
        )
      ) {}
  val sweepStateMachine =
    SweepUiStateMachineImpl(
      nfcSessionUIStateMachine,
      moneyAmountUiStateMachine
    )
  val props =
    SweepUiProps(
      sweepData = GeneratingPsbtsData(App),
      fiatCurrency = USD,
      onExit = onExitCallback,
      presentationStyle = Root
    )

  test("no funds found") {
    sweepStateMachine.test(props) {
      awaitScreenWithBody<LoadingBodyModel>()
      updateProps(props.copy(sweepData = NoFundsFoundData(App, {})))

      awaitScreenWithBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("No funds found")
      }
    }
  }

  test("generate psbts failed - user chooses to cancel") {
    sweepStateMachine.test(props) {
      awaitScreenWithBody<LoadingBodyModel>()
      updateProps(
        props.copy(
          sweepData = GeneratePsbtsFailedData(App, FailedToListKeysets, retry = onRetryCallback)
        )
      )
      awaitScreenWithBody<FormBodyModel> {
        onBack!!()
      }
      onExitCalls.awaitItem()
    }
  }

  test("sweep and sign without hardware") {
    sweepStateMachine.test(props) {
      awaitScreenWithBody<LoadingBodyModel>()
      updateProps(
        props.copy(
          sweepData =
            PsbtsGeneratedData(
              recoveredFactor = App,
              totalFeeAmount = PsbtMock.fee,
              startSweep = { startSweepCalls += Unit }
            )
        )
      )
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe AppRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
        header.shouldNotBeNull().sublineModel.shouldNotBeNull().string.shouldContain("10,000 sats")
        primaryButton.shouldNotBeNull().onClick()
      }
      startSweepCalls.awaitItem()
      updateProps(props.copy(sweepData = SigningAndBroadcastingSweepsData(App)))
      awaitScreenWithBody<LoadingBodyModel> {
        id shouldBe AppRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_BROADCASTING
      }
      updateProps(props.copy(sweepData = SweepCompleteData(App, {})))
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe AppRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SUCCESS
        primaryButton.shouldNotBeNull().onClick()
      }
    }
  }

  test("sweep and sign with hardware") {
    sweepStateMachine.test(props) {
      awaitScreenWithBody<LoadingBodyModel>()
      val sweepPsbts =
        listOf(
          SweepPsbt(
            PsbtMock.copy(id = "app-sign"),
            App,
            SpendingKeysetMock
          ),
          SweepPsbt(
            PsbtMock.copy(id = "hw-sign"),
            Hardware,
            SpendingKeysetMock2
          )
        )
      val totalFeeAmount =
        PsbtMock.fee + PsbtMock.fee
      val needsHwSign = mapOf(SpendingKeysetMock2 to sweepPsbts[1].psbt).toImmutableMap()
      val hwSignedPsbts = immutableListOf(sweepPsbts[1].psbt.copy(base64 = "hw-signed"))
      updateProps(
        props.copy(
          sweepData =
            PsbtsGeneratedData(
              recoveredFactor = App,
              totalFeeAmount = totalFeeAmount,
              startSweep = { startSweepCalls += Unit }
            )
        )
      )
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe AppRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
        header.shouldNotBeNull().sublineModel.shouldNotBeNull().string.shouldContain(
          "10,000 sats ($100.00)"
        )
        primaryButton.shouldNotBeNull().onClick()
      }
      startSweepCalls.awaitItem()
      updateProps(
        props.copy(
          sweepData =
            AwaitingHardwareSignedSweepsData(
              recoveredFactor = App,
              keyboxConfig = KeyboxConfigMock,
              needsHwSign = needsHwSign,
              addHwSignedSweeps = { addHwSignedSweepsCalls += it }
            )
        )
      )
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<ImmutableList<Psbt>>>(
        id = nfcSessionUIStateMachine.id
      ) {
        val nfcCommandsMock = NfcCommandsMock(turbines::create)
        session(NfcSessionFake(), nfcCommandsMock)
        needsHwSign.forEach { nfcCommandsMock.signTransactionCalls.awaitItem() shouldBe it.value }
        isHardwareFake shouldBe KeyboxConfigMock.isHardwareFake
        onSuccess(hwSignedPsbts)
      }
      addHwSignedSweepsCalls.awaitItem().shouldBe(hwSignedPsbts)
      updateProps(props.copy(sweepData = SigningAndBroadcastingSweepsData(App)))
      awaitScreenWithBody<LoadingBodyModel> {
        id shouldBe AppRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_BROADCASTING
      }
      updateProps(props.copy(sweepData = SweepCompleteData(App, {})))
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe AppRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SUCCESS
        primaryButton!!.onClick()
      }
    }
  }

  test("broadcast failed - user chooses to cancel") {
    sweepStateMachine.test(props) {
      awaitScreenWithBody<LoadingBodyModel>()
      updateProps(
        props.copy(
          sweepData =
            PsbtsGeneratedData(
              recoveredFactor = App,
              totalFeeAmount = PsbtMock.fee,
              startSweep = { startSweepCalls += Unit }
            )
        )
      )
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe AppRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
        header.shouldNotBeNull().sublineModel.shouldNotBeNull().string.shouldContain(
          "10,000 sats ($100.00)"
        )
        primaryButton!!.onClick()
      }
      startSweepCalls.awaitItem()
      updateProps(props.copy(sweepData = SigningAndBroadcastingSweepsData(App)))
      awaitScreenWithBody<LoadingBodyModel>()
      updateProps(
        props.copy(
          sweepData = SweepFailedData(App, Generic(Exception("Dang."), null), onRetryCallback)
        )
      )
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe AppRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_FAILED
        secondaryButton!!.onClick()
      }
      onExitCalls.awaitItem()
    }
  }

  test("broadcast failed - user chooses to retry") {
    sweepStateMachine.test(props) {
      awaitScreenWithBody<LoadingBodyModel>()
      updateProps(
        props.copy(
          sweepData =
            PsbtsGeneratedData(
              recoveredFactor = App,
              totalFeeAmount = PsbtMock.fee,
              startSweep = { startSweepCalls += Unit }
            )
        )
      )
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe AppRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
        header.shouldNotBeNull().sublineModel.shouldNotBeNull().string.shouldContain("10,000 sats")
        primaryButton!!.onClick()
      }
      startSweepCalls.awaitItem()
      updateProps(props.copy(sweepData = SigningAndBroadcastingSweepsData(App)))
      awaitScreenWithBody<LoadingBodyModel>()
      updateProps(
        props.copy(
          sweepData = SweepFailedData(App, Generic(Exception("Dang."), null), onRetryCallback)
        )
      )
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe AppRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_FAILED
        primaryButton!!.onClick()
      }
      onRetryCalls.awaitItem()

      // Go back to initial state
      updateProps(props)
      awaitScreenWithBody<LoadingBodyModel> {
        id shouldBe AppRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
      }
    }
  }
})
