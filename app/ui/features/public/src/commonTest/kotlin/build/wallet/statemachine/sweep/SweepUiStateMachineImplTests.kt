package build.wallet.statemachine.sweep

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.InactiveWalletSweepEventTrackerScreenId
import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.bdk.bindings.BdkError.Generic
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.PrivateSpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.recovery.sweep.SweepContext
import build.wallet.recovery.sweep.SweepPsbt
import build.wallet.recovery.sweep.SweepSignaturePlan
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.StateMachineMock
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
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
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.Required
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachineImpl
import build.wallet.statemachine.send.TransactionDetailModelType
import build.wallet.statemachine.send.TransferConfirmationScreenModel
import build.wallet.statemachine.send.TransferConfirmationScreenVariant
import build.wallet.statemachine.send.TransferInitiatedBodyModel
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
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
  val sweepProceedCalls = turbines.create<Unit>("sweep proceed calls")
  val addHwSignedSweepsCalls =
    turbines.create<Set<Psbt>>("add hw signed psbts calls")
  val nfcCommandsMock = NfcCommandsMock(turbine = turbines::create)

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
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
  val sweepStateMachine = SweepUiStateMachineImpl(
    nfcSessionUIStateMachine,
    moneyAmountUiStateMachine,
    fiatCurrencyPreferenceRepository,
    sweepDataStateMachine,
    inAppBrowserNavigator
  )
  val props = SweepUiProps(
    onExit = onExitCallback,
    presentationStyle = Root,
    keybox = KeyboxMock,
    sweepContext = SweepContext.Recovery(App),
    onSuccess = {},
    hasAttemptedSweep = false,
    onAttemptSweep = {}
  )

  beforeTest {
    fiatCurrencyPreferenceRepository.reset()
    sweepDataStateMachine.reset()
  }

  test("no funds found") {
    sweepStateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(NoFundsFoundData(proceed = {}))
      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("No funds found")
      }
    }
  }

  test("sweep already completed with no data shows success") {
    sweepStateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        SweepCompleteNoData(
          proceed = { sweepProceedCalls += Unit }
        )
      )
      awaitBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SUCCESS
        primaryButton!!.onClick()
      }
      sweepProceedCalls.awaitItem()
    }
  }

  test("generate psbts failed - user chooses to cancel") {
    sweepStateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        GeneratePsbtsFailedData(Error("oops"), retry = onRetryCallback)
      )
      awaitBody<FormBodyModel> {
        onBack!!()
      }
      onExitCalls.awaitItem()
    }
  }

  test("sweep and sign without hardware") {
    sweepStateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          totalFeeAmount = PsbtMock.fee.amount,
          totalTransferAmount = PsbtMock.amountBtc,
          destinationAddress = "bc1qtest",
          startSweep = { startSweepCalls += Unit }
        )
      )
      awaitBody<FormBodyModel> {
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
      awaitBody<LoadingSuccessBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_BROADCASTING
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        SweepCompleteData(
          proceed = {},
          totalFeeAmount = PsbtMock.fee.amount,
          totalTransferAmount = PsbtMock.amountBtc,
          destinationAddress = "bc1qtest"
        )
      )
      awaitBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SUCCESS
        clickPrimaryButton()
      }
    }
  }

  test("sweep and sign with hardware for lost app recovery") {
    sweepStateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      val sweepPsbts =
        listOf(
          SweepPsbt(
            PsbtMock.copy(id = "app-sign"),
            SweepSignaturePlan.AppAndServer,
            SpendingKeysetMock,
            "bc1qtest"
          ),
          SweepPsbt(
            PsbtMock.copy(id = "hw-sign"),
            SweepSignaturePlan.HardwareAndServer,
            PrivateSpendingKeysetMock,
            "bc1qtest"
          )
        )
      val totalFeeAmount =
        PsbtMock.fee.amount + PsbtMock.fee.amount
      val totalTransferAmount = PsbtMock.amountBtc + PsbtMock.amountBtc
      val needsHwSign = sweepPsbts.take(1).toSet()
      val hwSignedPsbts = setOf(sweepPsbts[1].psbt.copy(base64 = "hw-signed"))
      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          totalFeeAmount,
          totalTransferAmount,
          destinationAddress = "bc1qtest",
          startSweep = { startSweepCalls += Unit }
        )
      )
      awaitBody<FormBodyModel> {
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
          needsHwSign = needsHwSign,
          addHwSignedSweeps = { addHwSignedSweepsCalls += it }
        )
      )
      awaitBodyMock<NfcSessionUIStateMachineProps<Set<Psbt>>>(
        id = nfcSessionUIStateMachine.id
      ) {
        session(NfcSessionFake(), nfcCommandsMock)
        needsHwSign.forEach { nfcCommandsMock.signTransactionCalls.awaitItem() shouldBe it.psbt }
        shouldShowLongRunningOperation.shouldBeTrue()
        hardwareVerification.shouldBe(Required(true))
        onSuccess(hwSignedPsbts)
      }
      addHwSignedSweepsCalls.awaitItem().shouldBe(hwSignedPsbts)
      sweepDataStateMachine.emitModel(SigningAndBroadcastingSweepsData)
      awaitBody<LoadingSuccessBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_BROADCASTING
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        SweepCompleteData(
          proceed = {},
          totalFeeAmount = totalFeeAmount,
          totalTransferAmount = totalTransferAmount,
          destinationAddress = "bc1qtest"
        )
      )
      awaitBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SUCCESS
        primaryButton!!.onClick()
      }
    }
  }

  test("private wallet migration sweep completion shows transfer initiated screen") {
    val migrationProps = props.copy(sweepContext = SweepContext.PrivateWalletMigration)

    sweepStateMachine.test(migrationProps) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          totalFeeAmount = PsbtMock.fee.amount,
          totalTransferAmount = PsbtMock.amountBtc,
          destinationAddress = "bc1qtest",
          startSweep = { startSweepCalls += Unit }
        )
      )
      awaitBody<TransferConfirmationScreenModel> {
        variant shouldBe TransferConfirmationScreenVariant.PrivateWalletMigration
        primaryButton!!.onClick()
      }
      startSweepCalls.awaitItem()
      sweepDataStateMachine.emitModel(SigningAndBroadcastingSweepsData)
      awaitBody<LoadingSuccessBodyModel> {
        id shouldBe WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_BROADCASTING
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        SweepCompleteData(
          proceed = { sweepProceedCalls += Unit },
          totalFeeAmount = PsbtMock.fee.amount,
          totalTransferAmount = PsbtMock.amountBtc,
          destinationAddress = "bc1qtest"
        )
      )
      awaitBody<TransferInitiatedBodyModel> {
        recipientAddress.shouldBe(BitcoinAddress("bc1qtest"))
        transactionDetails.transactionDetailModelType.shouldBeInstanceOf<TransactionDetailModelType.Regular>().should {
            regular ->
          regular.totalAmountPrimaryText.shouldContain("10,000 sats")
        }
        clickPrimaryButton()
      }
      sweepProceedCalls.awaitItem()
    }
  }

  test("sweep and sign with hardware") {
    sweepStateMachine.test(props.copy(sweepContext = SweepContext.InactiveWallet)) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      val sweepPsbts = listOf(
        SweepPsbt(
          PsbtMock.copy(id = "app-sign"),
          SweepSignaturePlan.AppAndServer,
          SpendingKeysetMock,
          "bc1qtest"
        ),
        SweepPsbt(
          PsbtMock.copy(id = "hw-sign"),
          SweepSignaturePlan.HardwareAndServer,
          PrivateSpendingKeysetMock,
          "bc1qtest"
        )
      )
      val totalFeeAmount =
        PsbtMock.fee.amount + PsbtMock.fee.amount
      val totalTransferAmount = PsbtMock.amountBtc + PsbtMock.amountBtc
      val needsHwSign = sweepPsbts.take(1).toSet()
      val hwSignedPsbts = setOf(sweepPsbts[1].psbt.copy(base64 = "hw-signed"))
      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          totalFeeAmount,
          totalTransferAmount,
          destinationAddress = "bc1qtest",
          startSweep = { startSweepCalls += Unit }
        )
      )
      awaitBody<FormBodyModel> {
        clickPrimaryButton()
      }
      startSweepCalls.awaitItem()
      sweepDataStateMachine.emitModel(
        AwaitingHardwareSignedSweepsData(
          needsHwSign = needsHwSign,
          addHwSignedSweeps = { addHwSignedSweepsCalls += it }
        )
      )
      awaitBodyMock<NfcSessionUIStateMachineProps<Set<Psbt>>>(
        id = nfcSessionUIStateMachine.id
      ) {
        session(NfcSessionFake(), nfcCommandsMock)
        needsHwSign.forEach { nfcCommandsMock.signTransactionCalls.awaitItem() shouldBe it.psbt }
        shouldShowLongRunningOperation.shouldBeTrue()
        hardwareVerification.shouldBe(Required())
        onSuccess(hwSignedPsbts)
      }
      addHwSignedSweepsCalls.awaitItem().shouldBe(hwSignedPsbts)
      sweepDataStateMachine.emitModel(SigningAndBroadcastingSweepsData)
      awaitBody<LoadingSuccessBodyModel> {
        id shouldBe InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_BROADCASTING
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        SweepCompleteData(
          proceed = {},
          totalFeeAmount = totalFeeAmount,
          totalTransferAmount = totalTransferAmount,
          destinationAddress = "bc1qtest"
        )
      )
      awaitBody<FormBodyModel> {
        id shouldBe InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_SUCCESS
        primaryButton!!.onClick()
      }
    }
  }

  test("broadcast failed - user chooses to cancel") {
    sweepStateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          totalFeeAmount = PsbtMock.fee.amount,
          totalTransferAmount = PsbtMock.amountBtc,
          destinationAddress = "bc1qtest",
          startSweep = { startSweepCalls += Unit }
        )
      )
      awaitBody<FormBodyModel> {
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
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        SweepFailedData(Generic(Exception("Dang."), null), onRetryCallback)
      )
      awaitBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_FAILED
        secondaryButton!!.onClick()
      }
      onExitCalls.awaitItem()
    }
  }

  test("broadcast failed - user chooses to retry") {
    sweepStateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          totalFeeAmount = PsbtMock.fee.amount,
          totalTransferAmount = PsbtMock.amountBtc,
          destinationAddress = "bc1qtest",
          startSweep = { startSweepCalls += Unit }
        )
      )
      awaitBody<FormBodyModel> {
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
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        SweepFailedData(Generic(Exception("Dang."), null), onRetryCallback)
      )
      awaitBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_FAILED
        primaryButton!!.onClick()
      }
      onRetryCalls.awaitItem()

      // Go back to initial state
      sweepDataStateMachine.emitModel(GeneratingPsbtsData)
      awaitBody<LoadingSuccessBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }

  test("Sweep Funds on Inactive Wallet") {
    sweepStateMachine.test(
      props.copy(
        sweepContext = SweepContext.InactiveWallet
      )
    ) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          totalFeeAmount = PsbtMock.fee.amount,
          totalTransferAmount = PsbtMock.amountBtc,
          destinationAddress = "bc1qtest",
          startSweep = { startSweepCalls += Unit }
        )
      )
      awaitBody<FormBodyModel> {
        id shouldBe InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_SIGN_PSBTS_PROMPT
        toolbar?.trailingAccessory.shouldNotBeNull()
        toolbar?.leadingAccessory.shouldNotBeNull()
        header.shouldNotBeNull()
        header?.sublineModel.shouldNotBeNull()
        mainContentList.size.shouldBe(2)
        mainContentList[0].shouldBeInstanceOf<FormMainContentModel.DataList>()
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
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        SweepFailedData(Generic(Exception("Dang."), null), onRetryCallback)
      )
      awaitBody<FormBodyModel> {
        id shouldBe InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_FAILED
        primaryButton!!.onClick()
      }
      onRetryCalls.awaitItem()

      // Go back to initial state
      sweepDataStateMachine.emitModel(GeneratingPsbtsData)
      awaitBody<LoadingSuccessBodyModel> {
        id shouldBe InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_GENERATING_PSBTS
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }

  test("Learn more button opens in-app browser") {
    sweepStateMachine.test(
      props.copy(
        sweepContext = SweepContext.InactiveWallet
      )
    ) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          totalFeeAmount = PsbtMock.fee.amount,
          totalTransferAmount = PsbtMock.amountBtc,
          destinationAddress = "bc1qtest",
          startSweep = { startSweepCalls += Unit }
        )
      )
      awaitBody<FormBodyModel> {
        id shouldBe InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_SIGN_PSBTS_PROMPT
        toolbar
          .shouldNotBeNull()
          .trailingAccessory
          .shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
          .model
          .onClick
          .shouldNotBeNull()
          .invoke()
      }

      awaitBody<FormBodyModel> {
        id shouldBe InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_HELP
        primaryButton!!.onClick()
      }

      awaitBody<InAppBrowserModel> {
        open()
      }

      inAppBrowserNavigator.onOpenCalls.awaitItem()
        .shouldBe("https://support.bitkey.world/hc/en-us/articles/28019865146516-How-do-I-access-funds-sent-to-a-previously-created-Bitkey-address")
      inAppBrowserNavigator.onCloseCallback.shouldNotBeNull().invoke()

      awaitBody<FormBodyModel>()
    }
  }
})
