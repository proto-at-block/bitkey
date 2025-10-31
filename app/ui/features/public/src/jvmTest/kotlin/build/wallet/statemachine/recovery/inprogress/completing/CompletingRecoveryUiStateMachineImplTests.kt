package build.wallet.statemachine.recovery.inprogress.completing

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.bitkey.challange.DelayNotifyChallenge
import build.wallet.bitkey.challange.SignedChallenge.HardwareSignedChallenge
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.cloud.backup.csek.*
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.PublicKey
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.nfc.transaction.*
import build.wallet.recovery.LocalRecoveryAttemptProgress
import build.wallet.recovery.RecoveryStatusServiceMock
import build.wallet.recovery.socrec.PostSocRecTaskRepositoryMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel.State.Loading
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.CreatingSpendingKeysData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.ProcessingDescriptorBackupsData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.*
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.inprogress.DelayAndNotifyNewKeyReady
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.ui.model.alert.ButtonAlertModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import okio.ByteString.Companion.encodeUtf8

@Suppress("LargeClass")
class CompletingRecoveryUiStateMachineImplTests : FunSpec({
  val onExitCalls = turbines.create<Unit>("onExit")
  val onCompleteCalls = turbines.create<Unit>("onComplete")
  val mockDelayNotifyChallenge = DelayNotifyChallenge("test-challenge")

  val postSocRecTaskRepository = PostSocRecTaskRepositoryMock()
  val recoveryStatusService = RecoveryStatusServiceMock(turbine = turbines::create)

  val proofOfPossessionNfcStateMachine = object : ProofOfPossessionNfcStateMachine,
    ScreenStateMachineMock<ProofOfPossessionNfcProps>(
      id = "proof-of-possession-nfc"
    ) {}

  val fullAccountCloudSignInAndBackupUiStateMachine =
    object : FullAccountCloudSignInAndBackupUiStateMachine,
      ScreenStateMachineMock<FullAccountCloudSignInAndBackupProps>(
        id = "full-account-cloud-sign-in-and-backup"
      ) {}

  val sweepUiStateMachine = object : SweepUiStateMachine,
    ScreenStateMachineMock<SweepUiProps>(
      id = "sweep-ui"
    ) {}

  val nfcSessionUIStateMachine = object : NfcSessionUIStateMachine,
    ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
      id = "nfc-session-ui"
    ) {}

  val eventTracker = EventTrackerMock(turbines::create)

  val stateMachine = CompletingRecoveryUiStateMachineImpl(
    proofOfPossessionNfcStateMachine = proofOfPossessionNfcStateMachine,
    fullAccountCloudSignInAndBackupUiStateMachine = fullAccountCloudSignInAndBackupUiStateMachine,
    sweepUiStateMachine = sweepUiStateMachine,
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    postSocRecTaskRepository = postSocRecTaskRepository,
    recoveryStatusService = recoveryStatusService,
    eventTracker = eventTracker
  )

  val baseProps = CompletingRecoveryUiProps(
    presentationStyle = ScreenPresentationStyle.Root,
    completingRecoveryData = ReadyToCompleteRecoveryData(
      canCancelRecovery = true,
      physicalFactor = PhysicalFactor.App,
      startComplete = {},
      cancel = {}
    ),
    onExit = { onExitCalls += Unit },
    onComplete = { onCompleteCalls += Unit }
  )

  beforeTest {
    postSocRecTaskRepository.reset()
    recoveryStatusService.reset()
  }

  test("ReadyToCompleteRecoveryData for App factor shows DelayAndNotifyNewKeyReady") {
    val props = baseProps.copy(
      completingRecoveryData = ReadyToCompleteRecoveryData(
        canCancelRecovery = true,
        physicalFactor = PhysicalFactor.App,
        startComplete = {},
        cancel = {}
      )
    )

    stateMachine.test(props) {
      awaitBody<DelayAndNotifyNewKeyReady> {
        // Verify the UI shows the correct factor and has both buttons available
        factorToRecover.shouldBe(PhysicalFactor.App)
        onStopRecovery.shouldNotBeNull()
      }
    }
  }

  test("ReadyToCompleteRecoveryData for Hardware factor shows DelayAndNotifyNewKeyReady") {
    val props = baseProps.copy(
      completingRecoveryData = ReadyToCompleteRecoveryData(
        canCancelRecovery = true,
        physicalFactor = PhysicalFactor.Hardware,
        startComplete = {},
        cancel = {}
      )
    )

    stateMachine.test(props) {
      awaitBody<DelayAndNotifyNewKeyReady> {
        // Verify the UI shows the correct factor and has both buttons available
        factorToRecover.shouldBe(PhysicalFactor.Hardware)
        onStopRecovery.shouldNotBeNull()
      }
    }
  }

  test("ReadyToCompleteRecoveryData complete recovery button triggers callback") {
    val startCompleteCalls = turbines.create<Unit>("startComplete-test")

    val props = baseProps.copy(
      completingRecoveryData = ReadyToCompleteRecoveryData(
        canCancelRecovery = true,
        physicalFactor = PhysicalFactor.App,
        startComplete = { startCompleteCalls += Unit },
        cancel = {}
      )
    )

    stateMachine.test(props) {
      awaitBody<DelayAndNotifyNewKeyReady> {
        // Test that clicking complete recovery button triggers callback
        onCompleteRecovery()
      }

      // Verify complete callback was invoked
      startCompleteCalls.awaitItem()
    }
  }

  test("ReadyToCompleteRecoveryData when cannot cancel recovery shows DelayAndNotifyNewKeyReady with null onStopRecovery") {
    val props = baseProps.copy(
      completingRecoveryData = ReadyToCompleteRecoveryData(
        canCancelRecovery = false,
        physicalFactor = PhysicalFactor.Hardware,
        startComplete = {},
        cancel = {}
      )
    )

    stateMachine.test(props) {
      awaitBody<DelayAndNotifyNewKeyReady> {
        // Verify that onStopRecovery is null when cancellation is not allowed
        onStopRecovery.shouldBeNull()
      }
    }
  }

  test("ReadyToCompleteRecoveryData cancel recovery alert - confirm cancellation") {
    val cancelCalls = turbines.create<Unit>("cancel-confirm")

    val props = baseProps.copy(
      completingRecoveryData = ReadyToCompleteRecoveryData(
        canCancelRecovery = true,
        physicalFactor = PhysicalFactor.App,
        startComplete = {},
        cancel = { cancelCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<DelayAndNotifyNewKeyReady> {
        // Trigger the cancel recovery alert
        onStopRecovery.shouldNotBeNull().invoke()
      }

      // Wait for alert to appear and confirm cancellation
      awaitUntilScreenWithBody<DelayAndNotifyNewKeyReady>(
        matchingScreen = { it.alertModel != null }
      ) {
        alertModel.shouldBeTypeOf<ButtonAlertModel>()
          .onPrimaryButtonClick()
      }

      // Verify cancel callback was invoked
      cancelCalls.awaitItem()
    }
  }

  test("ReadyToCompleteRecoveryData cancel recovery alert - dismiss alert") {
    val cancelCalls = turbines.create<Unit>("cancel-dismiss")

    val props = baseProps.copy(
      completingRecoveryData = ReadyToCompleteRecoveryData(
        canCancelRecovery = true,
        physicalFactor = PhysicalFactor.App,
        startComplete = {},
        cancel = { cancelCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<DelayAndNotifyNewKeyReady> {
        // Trigger the cancel recovery alert
        onStopRecovery.shouldNotBeNull().invoke()
      }

      // Wait for alert to appear and dismiss it
      awaitUntilScreenWithBody<DelayAndNotifyNewKeyReady>(
        matchingScreen = { it.alertModel != null }
      ) {
        alertModel.shouldBeTypeOf<ButtonAlertModel>()
          .onSecondaryButtonClick?.invoke()
      }

      // Wait for alert to disappear
      awaitUntilScreenWithBody<DelayAndNotifyNewKeyReady>(
        matchingScreen = { it.alertModel == null }
      )

      // Verify cancel callback was NOT invoked
      cancelCalls.expectNoEvents()
    }
  }

  test("FailedToRotateAuthData shows ErrorFormBodyModel") {
    val onConfirmCalls = turbines.create<Unit>("onConfirm-rotate-auth")
    val props = baseProps.copy(
      completingRecoveryData = FailedToRotateAuthData(
        factorToRecover = PhysicalFactor.App,
        cause = Error("Test error"),
        onConfirm = { onConfirmCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        // Test that clicking the primary button triggers the callback
        primaryButton.shouldNotBeNull().onClick.invoke()
      }

      // Verify callback was invoked
      onConfirmCalls.awaitItem()
    }
  }

  test("AwaitingChallengeAndCsekSignedWithHardwareData shows NFC session") {
    val nfcTransaction = NfcTransactionMock(
      value = SignChallengeAndSealSeks.SignedChallengeAndSeks(
        signedChallenge = HardwareSignedChallenge(
          challenge = mockDelayNotifyChallenge,
          signature = "test-signature"
        ),
        csek = CsekFake,
        ssek = SsekFake,
        sealedCsek = SealedCsekFake,
        sealedSsek = SealedSsekFake
      )
    )
    val props = baseProps.copy(
      completingRecoveryData = AwaitingChallengeAndCsekSignedWithHardwareData(
        nfcTransaction = nfcTransaction
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<NfcSessionUIStateMachineProps<*>> {
        // Verify the NFC props are correctly configured
        eventTrackerContext.shouldNotBeNull()
        screenPresentationStyle.shouldBe(ScreenPresentationStyle.Root)
      }
    }
  }

  test("AwaitingChallengeAndCsekSignedWithHardwareData NFC success triggers callback") {
    val onSuccessCalls =
      turbines.create<SignChallengeAndSealSeks.SignedChallengeAndSeks>("challenge-nfc-success")
    val testValue = SignChallengeAndSealSeks.SignedChallengeAndSeks(
      signedChallenge = HardwareSignedChallenge(
        challenge = mockDelayNotifyChallenge,
        signature = "test-signature"
      ),
      csek = CsekFake,
      ssek = SsekFake,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake
    )
    val nfcTransaction = NfcTransactionMock(
      value = testValue,
      onSuccess = { onSuccessCalls += it }
    )
    val props = baseProps.copy(
      completingRecoveryData = AwaitingChallengeAndCsekSignedWithHardwareData(
        nfcTransaction = nfcTransaction
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<NfcSessionUIStateMachineProps<SignChallengeAndSealSeks.SignedChallengeAndSeks>> {
        // Simulate successful NFC transaction
        onSuccess(testValue)
      }

      // Verify the transaction's onSuccess callback was triggered
      onSuccessCalls.awaitItem()
    }
  }

  test("AwaitingChallengeAndCsekSignedWithHardwareData NFC cancel triggers callback") {
    val onCancelCalls = turbines.create<Unit>("challenge-nfc-cancel")
    val testValue = SignChallengeAndSealSeks.SignedChallengeAndSeks(
      signedChallenge = HardwareSignedChallenge(
        challenge = mockDelayNotifyChallenge,
        signature = "test-signature"
      ),
      csek = CsekFake,
      ssek = SsekFake,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake
    )
    val nfcTransaction = NfcTransactionMock(
      value = testValue,
      onCancel = { onCancelCalls += Unit }
    )
    val props = baseProps.copy(
      completingRecoveryData = AwaitingChallengeAndCsekSignedWithHardwareData(
        nfcTransaction = nfcTransaction
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<NfcSessionUIStateMachineProps<SignChallengeAndSealSeks.SignedChallengeAndSeks>> {
        // Simulate user canceling NFC transaction
        onCancel()
      }

      // Verify the transaction's onCancel callback was triggered
      onCancelCalls.awaitItem()
    }
  }

  test("FetchingSealedDelegatedDecryptionKeyStringData shows NFC session") {
    val nfcTransaction = NfcTransactionMock(
      value = UnsealData.UnsealedDataResult("unsealed-data".encodeUtf8())
    )
    val props = baseProps.copy(
      completingRecoveryData = FetchingSealedDelegatedDecryptionKeyStringData(
        nfcTransaction = nfcTransaction
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>()
    }
  }

  test("FetchingSealedDelegatedDecryptionKeyStringData NFC success triggers callback") {
    val onSuccessCalls = turbines.create<UnsealData.UnsealedDataResult>("fetch-nfc-success")
    val testValue = UnsealData.UnsealedDataResult("unsealed-data".encodeUtf8())
    val nfcTransaction = NfcTransactionMock(
      value = testValue,
      onSuccess = { onSuccessCalls += it }
    )
    val props = baseProps.copy(
      completingRecoveryData = FetchingSealedDelegatedDecryptionKeyStringData(
        nfcTransaction = nfcTransaction
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<NfcSessionUIStateMachineProps<UnsealData.UnsealedDataResult>> {
        // Simulate successful NFC transaction
        onSuccess(testValue)
      }

      // Verify the transaction's onSuccess callback was triggered
      onSuccessCalls.awaitItem()
    }
  }

  test("FetchingSealedDelegatedDecryptionKeyStringData NFC cancel triggers callback") {
    val onCancelCalls = turbines.create<Unit>("fetch-nfc-cancel")
    val testValue = UnsealData.UnsealedDataResult("unsealed-data".encodeUtf8())
    val nfcTransaction = NfcTransactionMock(
      value = testValue,
      onCancel = { onCancelCalls += Unit }
    )
    val props = baseProps.copy(
      completingRecoveryData = FetchingSealedDelegatedDecryptionKeyStringData(
        nfcTransaction = nfcTransaction
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<NfcSessionUIStateMachineProps<UnsealData.UnsealedDataResult>> {
        // Simulate user canceling NFC transaction
        onCancel()
      }

      // Verify the transaction's onCancel callback was triggered
      onCancelCalls.awaitItem()
    }
  }

  test("SealingDelegatedDecryptionKeyData shows NFC session") {
    val nfcTransaction = NfcTransactionMock(
      value = SealDelegatedDecryptionKey.SealedDataResult("sealed-data".encodeUtf8())
    )
    val props = baseProps.copy(
      completingRecoveryData = SealingDelegatedDecryptionKeyData(
        nfcTransaction = nfcTransaction
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>()
    }
  }

  test("SealingDelegatedDecryptionKeyData NFC success triggers callback") {
    val onSuccessCalls =
      turbines.create<SealDelegatedDecryptionKey.SealedDataResult>("seal-nfc-success")
    val testValue = SealDelegatedDecryptionKey.SealedDataResult("sealed-data".encodeUtf8())
    val nfcTransaction = NfcTransactionMock(
      value = testValue,
      onSuccess = { onSuccessCalls += it }
    )
    val props = baseProps.copy(
      completingRecoveryData = SealingDelegatedDecryptionKeyData(
        nfcTransaction = nfcTransaction
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<NfcSessionUIStateMachineProps<SealDelegatedDecryptionKey.SealedDataResult>> {
        // Simulate successful NFC transaction
        onSuccess(testValue)
      }

      // Verify the transaction's onSuccess callback was triggered
      onSuccessCalls.awaitItem()
    }
  }

  test("SealingDelegatedDecryptionKeyData NFC cancel triggers callback") {
    val onCancelCalls = turbines.create<Unit>("seal-nfc-cancel")
    val testValue = SealDelegatedDecryptionKey.SealedDataResult("sealed-data".encodeUtf8())
    val nfcTransaction = NfcTransactionMock(
      value = testValue,
      onCancel = { onCancelCalls += Unit }
    )
    val props = baseProps.copy(
      completingRecoveryData = SealingDelegatedDecryptionKeyData(
        nfcTransaction = nfcTransaction
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<NfcSessionUIStateMachineProps<SealDelegatedDecryptionKey.SealedDataResult>> {
        // Simulate user canceling NFC transaction
        onCancel()
      }

      // Verify the transaction's onCancel callback was triggered
      onCancelCalls.awaitItem()
    }
  }

  test("RotatingAuthKeysWithF8eData shows LoadingBodyModel") {
    val props = baseProps.copy(
      completingRecoveryData = RotatingAuthKeysWithF8eData(
        physicalFactor = PhysicalFactor.App
      )
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
    }
  }

  test("CheckingCompletionAttemptData shows LoadingBodyModel") {
    val props = baseProps.copy(
      completingRecoveryData = CheckingCompletionAttemptData(
        physicalFactor = PhysicalFactor.App
      )
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
    }
  }

  test("FetchingSealedDelegatedDecryptionKeyFromF8eData shows LoadingBodyModel") {
    val props = baseProps.copy(
      completingRecoveryData = FetchingSealedDelegatedDecryptionKeyFromF8eData(
        physicalFactor = PhysicalFactor.App
      )
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
    }
  }

  test("RemovingTrustedContactsData shows LoadingBodyModel") {
    val props = baseProps.copy(
      completingRecoveryData = RemovingTrustedContactsData(
        physicalFactor = PhysicalFactor.App
      )
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
    }
  }

  test("DelegatedDecryptionKeyErrorStateData shows ErrorFormBodyModel") {
    val props = baseProps.copy(
      completingRecoveryData = DelegatedDecryptionKeyErrorStateData(
        physicalFactor = PhysicalFactor.App,
        cause = Error("Test error"),
        onRetry = {},
        onContinue = {}
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        // Verify the UI shows the correct form with both buttons
        primaryButton.shouldNotBeNull()
        secondaryButton.shouldNotBeNull()
      }
    }
  }

  test("DelegatedDecryptionKeyErrorStateData retry button triggers callback") {
    val onRetryCalls = turbines.create<Unit>("onRetry-ddk-error")
    val props = baseProps.copy(
      completingRecoveryData = DelegatedDecryptionKeyErrorStateData(
        physicalFactor = PhysicalFactor.App,
        cause = Error("Test error"),
        onRetry = { onRetryCalls += Unit },
        onContinue = {}
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        // Test that clicking the primary button (retry) triggers callback
        primaryButton.shouldNotBeNull().onClick.invoke()
      }

      // Verify callback was invoked
      onRetryCalls.awaitItem()
    }
  }

  test("DelegatedDecryptionKeyErrorStateData continue button triggers callback") {
    val onContinueCalls = turbines.create<Unit>("onContinue-ddk-error")
    val props = baseProps.copy(
      completingRecoveryData = DelegatedDecryptionKeyErrorStateData(
        physicalFactor = PhysicalFactor.App,
        cause = Error("Test error"),
        onRetry = {},
        onContinue = { onContinueCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        // Test that clicking the secondary button (continue) triggers callback
        secondaryButton.shouldNotBeNull().onClick.invoke()
      }

      // Verify callback was invoked
      onContinueCalls.awaitItem()
    }
  }

  test("AwaitingHardwareProofOfPossessionData shows ProofOfPossessionNfcStateMachine") {
    val props = baseProps.copy(
      completingRecoveryData = AwaitingHardwareProofOfPossessionData(
        fullAccountId = FullAccountIdMock,
        appAuthKey = PublicKey("test-key"),
        addHwFactorProofOfPossession = {},
        rollback = {}
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<ProofOfPossessionNfcProps>()
    }
  }

  test("CreatingSpendingKeysWithF8EData shows LoadingBodyModel") {
    val props = baseProps.copy(
      completingRecoveryData = CreatingSpendingKeysWithF8EData(
        physicalFactor = PhysicalFactor.App
      )
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
    }
  }

  test("FailedToCreateSpendingKeysData shows ErrorFormBodyModel") {
    val onRetryCalls = turbines.create<Unit>("onRetry-create-spending-keys")
    val props = baseProps.copy(
      completingRecoveryData = FailedToCreateSpendingKeysData(
        physicalFactor = PhysicalFactor.App,
        cause = Error("Test error"),
        onRetry = { onRetryCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        // Test that clicking the primary button (retry) triggers callback
        primaryButton.shouldNotBeNull().onClick.invoke()
      }

      // Verify callback was invoked
      onRetryCalls.awaitItem()
    }
  }

  test("RegeneratingTcCertificatesData shows LoadingBodyModel") {
    val props = baseProps.copy(
      completingRecoveryData = RegeneratingTcCertificatesData
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
    }
  }

  test("FailedRegeneratingTcCertificatesData shows ErrorFormBodyModel") {
    val retryCalls = turbines.create<Unit>("retry-regen-tc-certs")
    val props = baseProps.copy(
      completingRecoveryData = FailedRegeneratingTcCertificatesData(
        physicalFactor = PhysicalFactor.App,
        cause = Error("Test error"),
        retry = { retryCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        // Test that clicking the primary button (retry) triggers callback
        primaryButton.shouldNotBeNull().onClick.invoke()
      }

      // Verify callback was invoked
      retryCalls.awaitItem()
    }
  }

  test("PerformingCloudBackupData shows FullAccountCloudSignInAndBackupUiStateMachine") {
    val onBackupFinishedCalls = turbines.create<Unit>("onBackupFinished-cloud")
    val onBackupFailedCalls = turbines.create<Throwable?>("onBackupFailed-cloud")
    val props = baseProps.copy(
      completingRecoveryData = PerformingCloudBackupData(
        sealedCsek = SealedCsekFake,
        keybox = KeyboxMock,
        onBackupFinished = { onBackupFinishedCalls += Unit },
        onBackupFailed = { onBackupFailedCalls += it }
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<FullAccountCloudSignInAndBackupProps>()
    }
  }

  test("PerformingSweepData shows SweepUiStateMachine") {
    val props = baseProps.copy(
      completingRecoveryData = PerformingSweepData(
        physicalFactor = PhysicalFactor.App,
        keybox = KeyboxMock,
        rollback = {},
        hasAttemptedSweep = false
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<SweepUiProps>()
    }
  }

  test("ExitedPerformingSweepData shows ErrorFormBodyModel") {
    val retryCalls = turbines.create<Unit>("retry-sweep")
    val props = baseProps.copy(
      completingRecoveryData = ExitedPerformingSweepData(
        physicalFactor = PhysicalFactor.App,
        retry = { retryCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        // Test that clicking the primary button (retry) triggers callback
        primaryButton.shouldNotBeNull().onClick.invoke()
      }

      // Verify callback was invoked
      retryCalls.awaitItem()
    }
  }

  test("PerformingDdkBackupData shows LoadingBodyModel") {
    val props = baseProps.copy(
      completingRecoveryData = PerformingDdkBackupData(
        physicalFactor = PhysicalFactor.App
      )
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel>()
    }
  }

  test("FailedPerformingDdkBackupData shows ErrorFormBodyModelWithOptionalErrorData") {
    val retryCalls = turbines.create<Unit>("retry-ddk-backup")
    val props = baseProps.copy(
      completingRecoveryData = FailedPerformingDdkBackupData(
        physicalFactor = PhysicalFactor.App,
        cause = Error("Test error"),
        retry = { retryCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        // Test that clicking the primary button (retry) triggers callback
        primaryButton.shouldNotBeNull().onClick.invoke()
      }

      // Verify callback was invoked
      retryCalls.awaitItem()
    }
  }

  test("FailedPerformingCloudBackupData shows ErrorFormBodyModel") {
    val retryCalls = turbines.create<Unit>("retry-cloud-backup")
    val props = baseProps.copy(
      completingRecoveryData = FailedPerformingCloudBackupData(
        keybox = KeyboxMock,
        physicalFactor = PhysicalFactor.App,
        cause = Error("Test error"),
        retry = { retryCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        // Test that clicking the primary button (retry) triggers callback
        primaryButton.shouldNotBeNull().onClick.invoke()
      }

      // Verify callback was invoked
      retryCalls.awaitItem()
    }
  }

  test("AwaitingSsekUnsealingData shows NFC session") {
    val props = baseProps.copy(
      completingRecoveryData = AwaitingSsekUnsealingData(
        physicalFactor = PhysicalFactor.App,
        nfcTransaction = UnsealSsek(
          sealedSsek = SealedSsekFake,
          success = { },
          failure = { }
        )
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>()
    }
  }

  test("AwaitingSsekUnsealingData NFC success triggers callback") {
    val onSuccessCalls = turbines.create<Ssek>("ssek-nfc-success")
    val testValue = Ssek(SymmetricKeyImpl(raw = "unsealed-ssek".encodeUtf8()))
    val nfcTransaction = UnsealSsek(
      sealedSsek = SealedSsekFake,
      success = { onSuccessCalls += it },
      failure = { }
    )
    val props = baseProps.copy(
      completingRecoveryData = AwaitingSsekUnsealingData(
        physicalFactor = PhysicalFactor.App,
        nfcTransaction = nfcTransaction
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<NfcSessionUIStateMachineProps<Ssek>> {
        // Simulate successful NFC transaction
        onSuccess(testValue)
      }

      // Verify the transaction's onSuccess callback was triggered
      onSuccessCalls.awaitItem()
    }
  }

  test("UploadingDescriptorBackupsData shows LoadingBodyModel") {
    val props = baseProps.copy(
      completingRecoveryData = UploadingDescriptorBackupsData(
        physicalFactor = PhysicalFactor.App
      )
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(Loading)
      }
    }
  }

  test("HandlingDescriptorEncryption shows LoadingBodyModel") {
    val props = baseProps.copy(
      completingRecoveryData = HandlingDescriptorEncryption(
        physicalFactor = PhysicalFactor.App
      )
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(Loading)
      }
    }
  }

  test("RetrievingDescriptorsForKeyboxData shows LoadingBodyModel") {
    val props = baseProps.copy(
      completingRecoveryData = RetrievingDescriptorsForKeyboxData(
        physicalFactor = PhysicalFactor.App
      )
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(Loading)
      }
    }
  }

  test("FailedToProcessDescriptorBackupsData shows ErrorFormBodyModel") {
    val onRetryCalls = turbines.create<Unit>("onRetry-descriptor-backups")
    val props = baseProps.copy(
      completingRecoveryData = FailedToProcessDescriptorBackupsData(
        physicalFactor = PhysicalFactor.App,
        cause = Error("Test error"),
        onRetry = { onRetryCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        // Test that clicking the primary button (retry) triggers callback
        primaryButton.shouldNotBeNull().onClick.invoke()
      }

      // Verify callback was invoked
      onRetryCalls.awaitItem()
    }
  }

  test("ActivatingSpendingKeysetData shows LoadingBodyModel") {
    val props = baseProps.copy(
      completingRecoveryData = ActivatingSpendingKeysetData(
        physicalFactor = PhysicalFactor.App
      )
    )

    stateMachine.test(props) {
      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBe(Loading)
      }
    }
  }

  test("AwaitingHardwareProofOfPossessionForActivationData shows ProofOfPossessionNfcStateMachine") {
    val props = baseProps.copy(
      completingRecoveryData = AwaitingHardwareProofOfPossessionForActivationData(
        physicalFactor = PhysicalFactor.Hardware,
        fullAccountId = FullAccountIdMock,
        appAuthKey = PublicKey("test-key"),
        addHardwareProofOfPossession = {},
        rollback = {}
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<ProofOfPossessionNfcProps>()
    }
  }

  test("FailedToActivateSpendingKeysetData shows ErrorFormBodyModel") {
    val onRetryCalls = turbines.create<Unit>("onRetry-activate-spending-keyset")
    val props = baseProps.copy(
      completingRecoveryData = FailedToActivateSpendingKeysetData(
        physicalFactor = PhysicalFactor.App,
        cause = Error("Test error"),
        onRetry = { onRetryCalls += Unit }
      )
    )

    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        // Test that clicking the primary button (retry) triggers callback
        primaryButton.shouldNotBeNull().onClick.invoke()
      }

      // Verify callback was invoked
      onRetryCalls.awaitItem()
    }
  }

  test("PerformingSweepData onAttemptSweep triggers recovery progress call") {
    val props = baseProps.copy(
      completingRecoveryData = PerformingSweepData(
        physicalFactor = PhysicalFactor.App,
        keybox = KeyboxMock,
        rollback = {},
        hasAttemptedSweep = false
      )
    )

    stateMachine.test(props) {
      awaitBodyMock<SweepUiProps> {
        // Simulate sweep attempt
        onAttemptSweep()
      }

      // Verify the transaction's onSuccess callback was triggered
      val recoveryProgress = recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
      recoveryProgress.shouldBe(LocalRecoveryAttemptProgress.SweepingFunds)
    }
  }
})
