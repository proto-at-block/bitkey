package build.wallet.statemachine.sweep

import app.cash.turbine.plusAssign
import bitkey.f8e.privilegedactions.AuthorizationStrategy
import bitkey.f8e.privilegedactions.AuthorizationStrategyType
import bitkey.f8e.privilegedactions.PrivilegedActionInstance
import bitkey.f8e.privilegedactions.PrivilegedActionType
import bitkey.privilegedaction.OutOfBandPrivilegedActionInstanceFake
import bitkey.privilegedactions.HardwareVerificationPrivilegedActionServiceFake
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.InactiveWalletSweepEventTrackerScreenId
import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.bdk.bindings.BdkError.Generic
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.KeyboxW3Mock
import build.wallet.bitkey.spending.PrivateSpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.nfc.platform.SweepSigningContext
import build.wallet.nfc.platform.SweepSigningContextBuilder
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.recovery.sweep.SweepContext
import build.wallet.recovery.sweep.SweepPsbt
import build.wallet.recovery.sweep.SweepSignaturePlan
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
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachineImpl
import build.wallet.statemachine.send.TransactionDetailModelType
import build.wallet.statemachine.send.TransferConfirmationScreenModel
import build.wallet.statemachine.send.TransferConfirmationScreenVariant
import build.wallet.statemachine.send.TransferInitiatedBodyModel
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcSessionUiProps
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcSessionUiStateMachineMock
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

// Large end-to-end coverage for the sweep flow; splitting would hurt cohesion.
@Suppress("LargeClass")
class SweepUiStateMachineImplTests : FunSpec({
  val verifyHardwareSerialPrivilegedActionInstanceFake = PrivilegedActionInstance(
    id = "fake-verify-hardware-serial-pa-id",
    privilegedActionType = PrivilegedActionType.VERIFY_HARDWARE_SERIAL,
    authorizationStrategy = AuthorizationStrategy.OutOfBand(
      authorizationStrategyType = AuthorizationStrategyType.OUT_OF_BAND
    )
  )

  val onExitCalls = turbines.create<Unit>("exit calls")
  val onExitCallback = { onExitCalls += Unit }
  val onRetryCalls = turbines.create<Unit>("retry calls")
  val onRetryCallback = { onRetryCalls += Unit }
  val startSweepCalls = turbines.create<Unit>("start sweep calls")
  val sweepProceedCalls = turbines.create<Unit>("sweep proceed calls")
  val addHwSignedSweepsCalls =
    turbines.create<Set<Psbt>>("add hw signed psbts calls")
  val cancelHwSignCalls = turbines.create<Unit>("cancel hw sign calls")

  val signTransactionNfcSessionUiStateMachine = SignTransactionNfcSessionUiStateMachineMock("sign-tx-nfc")
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
  val hardwareVerificationPrivilegedActionService = HardwareVerificationPrivilegedActionServiceFake()
  val sweepSigningContextBuilder = object : SweepSigningContextBuilder {
    override fun buildFor(
      oldKeyset: SpendingKeyset,
      currentAccountIndex: UInt,
    ): SweepSigningContext? = null
  }
  val sweepStateMachine = SweepUiStateMachineImpl(
    hardwareVerificationPrivilegedActionService,
    signTransactionNfcSessionUiStateMachine,
    moneyAmountUiStateMachine,
    fiatCurrencyPreferenceRepository,
    sweepDataStateMachine,
    inAppBrowserNavigator,
    sweepSigningContextBuilder
  )
  val props = SweepUiProps(
    account = FullAccountMock,
    onExit = onExitCallback,
    presentationStyle = Root,
    sweepContext = SweepContext.Recovery(App),
    onSuccess = {},
    hasAttemptedSweep = false,
    onAttemptSweep = {}
  )

  beforeTest {
    fiatCurrencyPreferenceRepository.reset()
    sweepDataStateMachine.reset()
    hardwareVerificationPrivilegedActionService.pendingHardwareVerificationAction = null
    hardwareVerificationPrivilegedActionService.getPendingHardwareVerificationActionResult = null
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

  test("W3 no funds found shows step 4 of 4") {
    val upgradeProps = props.copy(
      account = FullAccountMock.copy(keybox = KeyboxW3Mock),
      sweepContext = SweepContext.W3Upgrade(replacedHardwareFingerprint = "old-w1-fingerprint")
    )

    sweepStateMachine.test(upgradeProps) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(NoFundsFoundData(proceed = {}))
      awaitBody<FormBodyModel> {
        id shouldBe WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_SWEEP_ZERO_BALANCE
        header.shouldNotBeNull().headline.shouldBeNull()
        formScreenTitle.shouldNotBeNull().eyebrow.shouldBe("Step 4 of 4")
        formScreenTitle.shouldNotBeNull().title.shouldBe("No funds found")
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

  test("hardware verification required shows informational screen") {
    // Keep a pending action so polling does not immediately dismiss the screen
    hardwareVerificationPrivilegedActionService.pendingHardwareVerificationAction =
      verifyHardwareSerialPrivilegedActionInstanceFake

    sweepStateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        HardwareVerificationRequiredData(
          privilegedActionInstance = OutOfBandPrivilegedActionInstanceFake,
          dismiss = onRetryCallback
        )
      )
      awaitBody<LoadingSuccessBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_HARDWARE_VERIFICATION_REQUIRED
        message.shouldBe("Waiting for verification...")
        primaryButton.shouldNotBeNull().text.shouldBe("Resend email")
        secondaryButton.shouldBeNull()
      }
    }
  }

  test("hardware verification required dismisses when polling detects no pending action") {
    // pendingHardwareVerificationAction defaults to null, so the first poll resolves the action
    // (whether by Confirm, Cancel, or exhaustion) and dismisses back to the sweep flow.
    sweepStateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        HardwareVerificationRequiredData(
          privilegedActionInstance = OutOfBandPrivilegedActionInstanceFake,
          dismiss = onRetryCallback
        )
      )
      awaitBody<LoadingSuccessBodyModel> {
        message.shouldBe("Waiting for verification...")
      }
      // Polling sees no pending action and invokes dismiss (onRetryCallback)
      onRetryCalls.awaitItem()
    }
  }

  test("existing pending hardware verification locks sweep on entry") {
    hardwareVerificationPrivilegedActionService.pendingHardwareVerificationAction =
      verifyHardwareSerialPrivilegedActionInstanceFake

    sweepStateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitBody<LoadingSuccessBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_HARDWARE_VERIFICATION_REQUIRED
        message.shouldBe("Waiting for verification...")
        primaryButton.shouldNotBeNull().text.shouldBe("Resend email")
        secondaryButton.shouldBeNull()
      }
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
      val needsHwSign = sweepPsbts
        .filter { it.signaturePlan.requiresHardwareSignature }
        .toSet()
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
          addHwSignedSweeps = { addHwSignedSweepsCalls += it },
          cancelHwSign = { cancelHwSignCalls += Unit }
        )
      )
      // First, expect a loading screen while transitioning to sequential signing
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      // Then, for each PSBT to sign, expect the sign transaction NFC session
      val firstPsbtToSign = needsHwSign.first()
      val signedPsbt = firstPsbtToSign.psbt.copy(base64 = "hw-signed")
      awaitBodyMock<SignTransactionNfcSessionUiProps>(
        id = signTransactionNfcSessionUiStateMachine.id
      ) {
        psbt shouldBe firstPsbtToSign.psbt
        spendingKeyset shouldBe firstPsbtToSign.sourceKeyset
        eventTrackerContext shouldBe NfcEventTrackerScreenIdContext.SIGN_MANY_TRANSACTIONS
        onSuccess(signedPsbt)
      }
      addHwSignedSweepsCalls.awaitItem().shouldBe(setOf(signedPsbt))

      // After signing, we return to ShowingSweepState with stale data temporarily
      // This shows a loading screen - consume it before proceeding
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

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

  test("W3 upgrade sweep signs with W1 override and skips telemetry") {
    val upgradeProps = props.copy(
      account = FullAccountMock.copy(keybox = KeyboxW3Mock),
      sweepContext = SweepContext.W3Upgrade(replacedHardwareFingerprint = "old-w1-fingerprint")
    )

    sweepStateMachine.test(upgradeProps) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      val sweepPsbt = SweepPsbt(
        PsbtMock.copy(id = "w3-upgrade-hw-sign"),
        SweepSignaturePlan.HardwareAndServer,
        SpendingKeysetMock,
        "bc1qtest"
      )
      sweepDataStateMachine.emitModel(
        AwaitingHardwareSignedSweepsData(
          needsHwSign = setOf(sweepPsbt),
          addHwSignedSweeps = { addHwSignedSweepsCalls += it },
          cancelHwSign = { cancelHwSignCalls += Unit }
        )
      )

      awaitBodyMock<SignTransactionNfcSessionUiProps>(
        id = signTransactionNfcSessionUiStateMachine.id
      ) {
        psbt shouldBe sweepPsbt.psbt
        spendingKeyset shouldBe sweepPsbt.sourceKeyset
        skipPairingCheck shouldBe true
        skipFirmwareTelemetry shouldBe true
        hardwareTypeOverride shouldBe bitkey.account.HardwareType.W1
        requiredHardwareType shouldBe bitkey.account.HardwareType.W1
      }
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
      val needsHwSign = sweepPsbts
        .filter { it.signaturePlan.requiresHardwareSignature }
        .toSet()
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
          addHwSignedSweeps = { addHwSignedSweepsCalls += it },
          cancelHwSign = { cancelHwSignCalls += Unit }
        )
      )
      // First, expect a loading screen while transitioning to sequential signing
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      // Then, for each PSBT to sign, expect the sign transaction NFC session
      val firstPsbtToSign = needsHwSign.first()
      val signedPsbt = firstPsbtToSign.psbt.copy(base64 = "hw-signed")
      awaitBodyMock<SignTransactionNfcSessionUiProps>(
        id = signTransactionNfcSessionUiStateMachine.id
      ) {
        psbt shouldBe firstPsbtToSign.psbt
        spendingKeyset shouldBe firstPsbtToSign.sourceKeyset
        eventTrackerContext shouldBe NfcEventTrackerScreenIdContext.SIGN_MANY_TRANSACTIONS
        onSuccess(signedPsbt)
      }
      addHwSignedSweepsCalls.awaitItem().shouldBe(setOf(signedPsbt))

      // After signing, we return to ShowingSweepState with stale data temporarily
      // This shows a loading screen - consume it before proceeding
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

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

  test("NFC cancel during hardware signing returns to PSBT confirmation") {
    sweepStateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      val sweepPsbts = listOf(
        SweepPsbt(
          PsbtMock.copy(id = "hw-sign"),
          SweepSignaturePlan.HardwareAndServer,
          SpendingKeysetMock,
          "bc1qtest"
        )
      )
      val needsHwSign = sweepPsbts.toSet()
      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          PsbtMock.fee.amount,
          PsbtMock.amountBtc,
          destinationAddress = "bc1qtest",
          startSweep = { startSweepCalls += Unit }
        )
      )
      awaitBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
        clickPrimaryButton()
      }
      startSweepCalls.awaitItem()
      sweepDataStateMachine.emitModel(
        AwaitingHardwareSignedSweepsData(
          needsHwSign = needsHwSign,
          addHwSignedSweeps = { addHwSignedSweepsCalls += it },
          cancelHwSign = { cancelHwSignCalls += Unit }
        )
      )
      // First, expect a loading screen while transitioning to sequential signing
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      // Then expect the sign transaction NFC session
      awaitBodyMock<SignTransactionNfcSessionUiProps>(
        id = signTransactionNfcSessionUiStateMachine.id
      ) {
        // Simulate user cancelling the NFC session
        onBack()
      }
      // Verify cancelHwSign was called (which returns to PSBT confirmation screen)
      cancelHwSignCalls.awaitItem()
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          PsbtMock.fee.amount,
          PsbtMock.amountBtc,
          destinationAddress = "bc1qtest",
          startSweep = { startSweepCalls += Unit }
        )
      )
      awaitBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
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

  test("sweep and sign multiple PSBTs sequentially") {
    sweepStateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      // Create multiple PSBTs that need hardware signing
      val sweepPsbt1 = SweepPsbt(
        PsbtMock.copy(id = "hw-sign-1"),
        SweepSignaturePlan.HardwareAndServer,
        SpendingKeysetMock,
        "bc1qtest"
      )
      val sweepPsbt2 = SweepPsbt(
        PsbtMock.copy(id = "hw-sign-2"),
        SweepSignaturePlan.HardwareAndServer,
        PrivateSpendingKeysetMock,
        "bc1qtest"
      )
      val needsHwSign = setOf(sweepPsbt1, sweepPsbt2)
      val totalFeeAmount = PsbtMock.fee.amount + PsbtMock.fee.amount
      val totalTransferAmount = PsbtMock.amountBtc + PsbtMock.amountBtc

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
          addHwSignedSweeps = { addHwSignedSweepsCalls += it },
          cancelHwSign = { cancelHwSignCalls += Unit }
        )
      )

      // First, expect a loading screen while transitioning to sequential signing
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Sign first PSBT
      val signedPsbt1 = sweepPsbt1.psbt.copy(base64 = "hw-signed-1")
      awaitBodyMock<SignTransactionNfcSessionUiProps>(
        id = signTransactionNfcSessionUiStateMachine.id
      ) {
        psbt shouldBe sweepPsbt1.psbt
        spendingKeyset shouldBe sweepPsbt1.sourceKeyset
        onSuccess(signedPsbt1)
      }

      // Expect loading screen between PSBTs (ReadyToSignNextPsbt state)
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Sign second PSBT
      val signedPsbt2 = sweepPsbt2.psbt.copy(base64 = "hw-signed-2")
      awaitBodyMock<SignTransactionNfcSessionUiProps>(
        id = signTransactionNfcSessionUiStateMachine.id
      ) {
        psbt shouldBe sweepPsbt2.psbt
        spendingKeyset shouldBe sweepPsbt2.sourceKeyset
        onSuccess(signedPsbt2)
      }

      // Verify all signed PSBTs were collected
      addHwSignedSweepsCalls.awaitItem().shouldBe(setOf(signedPsbt1, signedPsbt2))

      // After signing, we return to ShowingSweepState with stale data temporarily
      // This shows a loading screen - consume it before proceeding
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // The data state machine transitions to SigningAndBroadcastingSweepsData
      sweepDataStateMachine.emitModel(SigningAndBroadcastingSweepsData)

      // Await the broadcasting screen
      awaitBody<LoadingSuccessBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_BROADCASTING
      }
    }
  }

  test("W3 hardware with multiple PSBTs shows warning screen before signing") {
    val w3Props = props.copy(account = FullAccountMock.copy(keybox = KeyboxW3Mock))
    sweepStateMachine.test(w3Props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      // Create multiple PSBTs that need hardware signing
      val sweepPsbt1 = SweepPsbt(
        PsbtMock.copy(id = "hw-sign-1"),
        SweepSignaturePlan.HardwareAndServer,
        SpendingKeysetMock,
        "bc1qtest"
      )
      val sweepPsbt2 = SweepPsbt(
        PsbtMock.copy(id = "hw-sign-2"),
        SweepSignaturePlan.HardwareAndServer,
        PrivateSpendingKeysetMock,
        "bc1qtest"
      )
      val needsHwSign = setOf(sweepPsbt1, sweepPsbt2)

      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          PsbtMock.fee.amount + PsbtMock.fee.amount,
          PsbtMock.amountBtc + PsbtMock.amountBtc,
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
          addHwSignedSweeps = { addHwSignedSweepsCalls += it },
          cancelHwSign = { cancelHwSignCalls += Unit }
        )
      )

      // First, expect a loading screen while transitioning
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // W3 with multiple PSBTs should show warning screen
      awaitBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_MULTIPLE_TRANSACTIONS_WARNING
        header.shouldNotBeNull()
        header!!.headline shouldBe "Multiple transactions to sign"
        // Click continue to proceed with signing
        primaryButton!!.onClick()
      }

      // After clicking continue, NFC session should start for first PSBT
      val signedPsbt1 = sweepPsbt1.psbt.copy(base64 = "hw-signed-1")
      awaitBodyMock<SignTransactionNfcSessionUiProps>(
        id = signTransactionNfcSessionUiStateMachine.id
      ) {
        psbt shouldBe sweepPsbt1.psbt
        spendingKeyset shouldBe sweepPsbt1.sourceKeyset
        onSuccess(signedPsbt1)
      }

      // Expect loading screen between PSBTs (ReadyToSignNextPsbt state)
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Sign second PSBT
      val signedPsbt2 = sweepPsbt2.psbt.copy(base64 = "hw-signed-2")
      awaitBodyMock<SignTransactionNfcSessionUiProps>(
        id = signTransactionNfcSessionUiStateMachine.id
      ) {
        psbt shouldBe sweepPsbt2.psbt
        spendingKeyset shouldBe sweepPsbt2.sourceKeyset
        onSuccess(signedPsbt2)
      }

      // Verify all signed PSBTs were collected
      addHwSignedSweepsCalls.awaitItem().shouldBe(setOf(signedPsbt1, signedPsbt2))

      // After signing, we return to ShowingSweepState with stale data temporarily
      // This shows a loading screen - consume it before test completes
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }

  test("W3 multiple transactions warning - user cancels goes back to sweep screen") {
    val w3Props = props.copy(account = FullAccountMock.copy(keybox = KeyboxW3Mock))
    sweepStateMachine.test(w3Props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      val sweepPsbt1 = SweepPsbt(
        PsbtMock.copy(id = "hw-sign-1"),
        SweepSignaturePlan.HardwareAndServer,
        SpendingKeysetMock,
        "bc1qtest"
      )
      val sweepPsbt2 = SweepPsbt(
        PsbtMock.copy(id = "hw-sign-2"),
        SweepSignaturePlan.HardwareAndServer,
        PrivateSpendingKeysetMock,
        "bc1qtest"
      )
      val needsHwSign = setOf(sweepPsbt1, sweepPsbt2)

      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          PsbtMock.fee.amount + PsbtMock.fee.amount,
          PsbtMock.amountBtc + PsbtMock.amountBtc,
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
          addHwSignedSweeps = { addHwSignedSweepsCalls += it },
          cancelHwSign = { cancelHwSignCalls += Unit }
        )
      )

      // First, expect a loading screen
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // W3 with multiple PSBTs should show warning screen - user goes back
      awaitBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_MULTIPLE_TRANSACTIONS_WARNING
        onBack!!()
      }

      // Verify cancelHwSign was called
      cancelHwSignCalls.awaitItem()
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          PsbtMock.fee.amount + PsbtMock.fee.amount,
          PsbtMock.amountBtc + PsbtMock.amountBtc,
          destinationAddress = "bc1qtest",
          startSweep = { startSweepCalls += Unit }
        )
      )
      awaitBody<FormBodyModel> {
        id shouldBe DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT
      }
    }
  }

  test("W3 hardware with single PSBT does NOT show warning screen") {
    val w3Props = props.copy(account = FullAccountMock.copy(keybox = KeyboxW3Mock))
    sweepStateMachine.test(w3Props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      // Only one PSBT
      val sweepPsbt = SweepPsbt(
        PsbtMock.copy(id = "hw-sign"),
        SweepSignaturePlan.HardwareAndServer,
        SpendingKeysetMock,
        "bc1qtest"
      )
      val needsHwSign = setOf(sweepPsbt)

      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          PsbtMock.fee.amount,
          PsbtMock.amountBtc,
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
          addHwSignedSweeps = { addHwSignedSweepsCalls += it },
          cancelHwSign = { cancelHwSignCalls += Unit }
        )
      )

      // First, expect a loading screen
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // With single PSBT on W3, should go directly to NFC signing (no warning)
      val signedPsbt = sweepPsbt.psbt.copy(base64 = "hw-signed")
      awaitBodyMock<SignTransactionNfcSessionUiProps>(
        id = signTransactionNfcSessionUiStateMachine.id
      ) {
        psbt shouldBe sweepPsbt.psbt
        spendingKeyset shouldBe sweepPsbt.sourceKeyset
        onSuccess(signedPsbt)
      }

      // Verify signed PSBT was collected
      addHwSignedSweepsCalls.awaitItem().shouldBe(setOf(signedPsbt))

      // After signing, we return to ShowingSweepState with stale data temporarily
      // This shows a loading screen - consume it before test completes
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }

  test("hardware signing passes correct PSBT and keyset to sign transaction SM") {
    sweepStateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      val sweepPsbt = SweepPsbt(
        PsbtMock.copy(id = "hw-sign"),
        SweepSignaturePlan.HardwareAndServer,
        PrivateSpendingKeysetMock,
        "bc1qtest"
      )
      val needsHwSign = setOf(sweepPsbt)

      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          PsbtMock.fee.amount,
          PsbtMock.amountBtc,
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
          addHwSignedSweeps = { addHwSignedSweepsCalls += it },
          cancelHwSign = { cancelHwSignCalls += Unit }
        )
      )

      // First, expect a loading screen while transitioning to sequential signing
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Verify the sign transaction SM receives correct props
      val signedPsbt = sweepPsbt.psbt.copy(base64 = "hw-signed")
      awaitBodyMock<SignTransactionNfcSessionUiProps>(
        id = signTransactionNfcSessionUiStateMachine.id
      ) {
        // Verify PSBT and keyset are correctly passed through
        psbt shouldBe sweepPsbt.psbt
        spendingKeyset shouldBe PrivateSpendingKeysetMock
        eventTrackerContext shouldBe NfcEventTrackerScreenIdContext.SIGN_MANY_TRANSACTIONS
        onSuccess(signedPsbt)
      }

      // Verify signed PSBT was collected
      addHwSignedSweepsCalls.awaitItem().shouldBe(setOf(signedPsbt))

      // After signing, we return to ShowingSweepState with stale data temporarily
      // This shows a loading screen - consume it before test completes
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }

  test("signing multiple PSBTs with different keysets passes correct keyset for each") {
    sweepStateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      // Create multiple PSBTs that need hardware signing with different keysets
      val sweepPsbt1 = SweepPsbt(
        PsbtMock.copy(id = "hw-sign-1"),
        SweepSignaturePlan.HardwareAndServer,
        SpendingKeysetMock,
        "bc1qtest"
      )
      val sweepPsbt2 = SweepPsbt(
        PsbtMock.copy(id = "hw-sign-2"),
        SweepSignaturePlan.HardwareAndServer,
        PrivateSpendingKeysetMock,
        "bc1qtest"
      )
      val needsHwSign = setOf(sweepPsbt1, sweepPsbt2)

      sweepDataStateMachine.emitModel(
        PsbtsGeneratedData(
          PsbtMock.fee.amount + PsbtMock.fee.amount,
          PsbtMock.amountBtc + PsbtMock.amountBtc,
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
          addHwSignedSweeps = { addHwSignedSweepsCalls += it },
          cancelHwSign = { cancelHwSignCalls += Unit }
        )
      )

      // First, expect a loading screen while transitioning to sequential signing
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Sign first PSBT - verify correct keyset
      val signedPsbt1 = sweepPsbt1.psbt.copy(base64 = "hw-signed-1")
      awaitBodyMock<SignTransactionNfcSessionUiProps>(
        id = signTransactionNfcSessionUiStateMachine.id
      ) {
        psbt shouldBe sweepPsbt1.psbt
        spendingKeyset shouldBe sweepPsbt1.sourceKeyset
        onSuccess(signedPsbt1)
      }

      // Expect loading screen between PSBTs (ReadyToSignNextPsbt state)
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Sign second PSBT - verify different keyset
      val signedPsbt2 = sweepPsbt2.psbt.copy(base64 = "hw-signed-2")
      awaitBodyMock<SignTransactionNfcSessionUiProps>(
        id = signTransactionNfcSessionUiStateMachine.id
      ) {
        psbt shouldBe sweepPsbt2.psbt
        spendingKeyset shouldBe sweepPsbt2.sourceKeyset
        onSuccess(signedPsbt2)
      }

      // Verify all signed PSBTs were collected
      addHwSignedSweepsCalls.awaitItem().shouldBe(setOf(signedPsbt1, signedPsbt2))

      // After signing, we return to ShowingSweepState with stale data temporarily
      // This shows a loading screen - consume it before test completes
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }
})
