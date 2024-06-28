package build.wallet.statemachine.sweep

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.InactiveWalletSweepEventTrackerScreenId
import build.wallet.bdk.bindings.BdkError.Generic
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock2
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.recovery.sweep.SweepPsbt
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.sweep.SweepData
import build.wallet.statemachine.data.recovery.sweep.SweepData.*
import build.wallet.statemachine.data.recovery.sweep.SweepDataProps
import build.wallet.statemachine.data.recovery.sweep.SweepDataStateMachine
import build.wallet.statemachine.money.amount.MoneyAmountModel
import build.wallet.statemachine.money.amount.MoneyAmountUiProps
import build.wallet.statemachine.money.amount.MoneyAmountUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachineImpl
import build.wallet.statemachine.ui.clickPrimaryButton
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class SweepUiStateMachineImplTests : FunSpec({
  val onExitCalls = turbines.create<Unit>("exit calls")
  val onExitCallback = { onExitCalls += Unit }
  val onRetryCalls = turbines.create<Unit>("retry calls")
  val onRetryCallback = { onRetryCalls += Unit }
  val startSweepCalls = turbines.create<Unit>("start sweep calls")
  val addHwSignedSweepsCalls =
    turbines.create<Set<Psbt>>("add hw signed psbts calls")

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
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val sweepDataStateMachine =
    object : SweepDataStateMachine, StateMachineMock<SweepDataProps, SweepData>(
      GeneratingPsbtsData
    ) {}
  val sweepStateMachine = SweepUiStateMachineImpl(
    nfcSessionUIStateMachine,
    moneyAmountUiStateMachine,
    fiatCurrencyPreferenceRepository,
    sweepDataStateMachine
  )
  val props = SweepUiProps(
    onExit = onExitCallback,
    presentationStyle = Root,
    keybox = KeyboxMock,
    recoveredFactor = App,
    onSuccess = {}
  )

  beforeTest {
    fiatCurrencyPreferenceRepository.reset()
    sweepDataStateMachine.reset()
  }

  test("no funds found") {
    sweepStateMachine.test(props) {
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(NoFundsFoundData(proceed = {}))
      awaitScreenWithBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("No funds found")
      }
    }
  }

  test("generate psbts failed - user chooses to cancel") {
    sweepStateMachine.test(props) {
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        GeneratePsbtsFailedData(Error("oops"), retry = onRetryCallback)
      )
      awaitScreenWithBody<FormBodyModel> {
        onBack!!()
      }
      onExitCalls.awaitItem()
    }
  }

  test("sweep and sign without hardware") {
    sweepStateMachine.test(props) {
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          totalFeeAmount = PsbtMock.fee,
          totalTransferAmount = PsbtMock.amountBtc,
          startSweep = { startSweepCalls += Unit }
        )
      )
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
        toolbar?.trailingAccessory.shouldBeNull()
        toolbar?.leadingAccessory.shouldBeNull()
        header.shouldNotBeNull()
        mainContentList.size.shouldBe(2)
        mainContentList[1].shouldBeInstanceOf<FormMainContentModel.DataList>().should { dataList ->
          dataList.items.should { items ->
            items.size.shouldBe(2)
            items[0].sideText.shouldContain("$100.00")
            items[1].sideText.shouldContain("$100.00")
          }
          dataList.total.should { totals ->
            totals?.sideText.shouldContain("$100.00")
            totals?.secondarySideText.shouldContain("10,000 sats")
          }
        }
        clickPrimaryButton()
      }
      startSweepCalls.awaitItem()
      sweepDataStateMachine.emitModel(SigningAndBroadcastingSweepsData)
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_BROADCASTING
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(SweepCompleteData({}))
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SUCCESS
        clickPrimaryButton()
      }
    }
  }

  test("sweep and sign with hardware") {
    sweepStateMachine.test(props) {
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
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
      val totalTransferAmount = PsbtMock.amountBtc + PsbtMock.amountBtc
      val needsHwSign = sweepPsbts.take(1).toSet()
      val hwSignedPsbts = setOf(sweepPsbts[1].psbt.copy(base64 = "hw-signed"))
      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          totalFeeAmount,
          totalTransferAmount,
          { startSweepCalls += Unit }
        )
      )
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
        toolbar?.trailingAccessory.shouldBeNull()
        toolbar?.leadingAccessory.shouldBeNull()
        header.shouldNotBeNull()
        mainContentList.size.shouldBe(2)
        mainContentList[1].shouldBeInstanceOf<FormMainContentModel.DataList>().should { dataList ->
          dataList.items.should { items ->
            items.size.shouldBe(2)
            items[0].sideText.shouldContain("$100.00")
            items[1].sideText.shouldContain("$100.00")
          }
          dataList.total.should { totals ->
            totals?.sideText.shouldContain("$100.00")
            totals?.secondarySideText.shouldContain("10,000 sats")
          }
        }
        clickPrimaryButton()
      }
      startSweepCalls.awaitItem()
      sweepDataStateMachine.emitModel(
        AwaitingHardwareSignedSweepsData(
          fullAccountConfig = FullAccountConfigMock,
          needsHwSign = needsHwSign,
          addHwSignedSweeps = { addHwSignedSweepsCalls += it }
        )
      )
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<Set<Psbt>>>(
        id = nfcSessionUIStateMachine.id
      ) {
        val nfcCommandsMock = NfcCommandsMock(turbine = turbines::create)
        session(NfcSessionFake(), nfcCommandsMock)
        needsHwSign.forEach { nfcCommandsMock.signTransactionCalls.awaitItem() shouldBe it.psbt }
        isHardwareFake shouldBe FullAccountConfigMock.isHardwareFake
        onSuccess(hwSignedPsbts)
      }
      addHwSignedSweepsCalls.awaitItem().shouldBe(hwSignedPsbts)
      sweepDataStateMachine.emitModel(SigningAndBroadcastingSweepsData)
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_BROADCASTING
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(SweepCompleteData({}))
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SUCCESS
        primaryButton!!.onClick()
      }
    }
  }

  test("broadcast failed - user chooses to cancel") {
    sweepStateMachine.test(props) {
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          totalFeeAmount = PsbtMock.fee,
          totalTransferAmount = PsbtMock.amountBtc,
          startSweep = { startSweepCalls += Unit }
        )
      )
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
        toolbar?.trailingAccessory.shouldBeNull()
        toolbar?.leadingAccessory.shouldBeNull()
        header.shouldNotBeNull()
        mainContentList.size.shouldBe(2)
        mainContentList[1].shouldBeInstanceOf<FormMainContentModel.DataList>().should { dataList ->
          dataList.items.should { items ->
            items.size.shouldBe(2)
            items[0].sideText.shouldContain("$100.00")
            items[1].sideText.shouldContain("$100.00")
          }
          dataList.total.should { totals ->
            totals?.sideText.shouldContain("$100.00")
            totals?.secondarySideText.shouldContain("10,000 sats")
          }
        }
        primaryButton!!.onClick()
      }
      startSweepCalls.awaitItem()
      sweepDataStateMachine.emitModel(SigningAndBroadcastingSweepsData)
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        SweepFailedData(Generic(Exception("Dang."), null), onRetryCallback)
      )
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_FAILED
        secondaryButton!!.onClick()
      }
      onExitCalls.awaitItem()
    }
  }

  test("broadcast failed - user chooses to retry") {
    sweepStateMachine.test(props) {
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          totalFeeAmount = PsbtMock.fee,
          totalTransferAmount = PsbtMock.amountBtc,
          startSweep = { startSweepCalls += Unit }
        )
      )
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
        toolbar?.trailingAccessory.shouldBeNull()
        toolbar?.leadingAccessory.shouldBeNull()
        header.shouldNotBeNull()
        mainContentList.size.shouldBe(2)
        mainContentList[1].shouldBeInstanceOf<FormMainContentModel.DataList>().should { dataList ->
          dataList.items.should { items ->
            items.size.shouldBe(2)
            items[0].sideText.shouldContain("$100.00")
            items[1].sideText.shouldContain("$100.00")
          }
          dataList.total.should { totals ->
            totals?.sideText.shouldContain("$100.00")
            totals?.secondarySideText.shouldContain("10,000 sats")
          }
        }
        primaryButton!!.onClick()
      }
      startSweepCalls.awaitItem()
      sweepDataStateMachine.emitModel(SigningAndBroadcastingSweepsData)
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        SweepFailedData(Generic(Exception("Dang."), null), onRetryCallback)
      )
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_FAILED
        primaryButton!!.onClick()
      }
      onRetryCalls.awaitItem()

      // Go back to initial state
      sweepDataStateMachine.emitModel(GeneratingPsbtsData)
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }

  test("Sweep Funds on Inactive Wallet") {
    sweepStateMachine.test(
      props.copy(
        recoveredFactor = null
      )
    ) {
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          totalFeeAmount = PsbtMock.fee,
          totalTransferAmount = PsbtMock.amountBtc,
          startSweep = { startSweepCalls += Unit }
        )
      )
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_SIGN_PSBTS_PROMPT
        toolbar?.trailingAccessory.shouldNotBeNull()
        toolbar?.leadingAccessory.shouldNotBeNull()
        header.shouldNotBeNull()
        header?.sublineModel.shouldNotBeNull()
        mainContentList.size.shouldBe(2)
        mainContentList[1].shouldBeInstanceOf<FormMainContentModel.DataList>().should { dataList ->
          dataList.items.should { items ->
            items.size.shouldBe(2)
            items[0].sideText.shouldContain("$100.00")
            items[1].sideText.shouldContain("$100.00")
          }
          dataList.total.should { totals ->
            totals?.sideText.shouldContain("$100.00")
            totals?.secondarySideText.shouldContain("10,000 sats")
          }
        }
        primaryButton!!.onClick()
      }
      startSweepCalls.awaitItem()
      sweepDataStateMachine.emitModel(SigningAndBroadcastingSweepsData)
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        SweepFailedData(Generic(Exception("Dang."), null), onRetryCallback)
      )
      awaitScreenWithBody<FormBodyModel> {
        id shouldBe InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_FAILED
        primaryButton!!.onClick()
      }
      onRetryCalls.awaitItem()

      // Go back to initial state
      sweepDataStateMachine.emitModel(GeneratingPsbtsData)
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        id shouldBe InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_GENERATING_PSBTS
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }
})
