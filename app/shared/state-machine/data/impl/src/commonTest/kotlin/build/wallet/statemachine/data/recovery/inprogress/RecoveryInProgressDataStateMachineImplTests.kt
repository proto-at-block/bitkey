package build.wallet.statemachine.data.recovery.inprogress

import app.cash.turbine.plusAssign
import build.wallet.auth.AccountAuthenticatorMock
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.CsekGeneratorMock
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.SpecificClientErrorMock
import build.wallet.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import build.wallet.f8e.relationships.RelationshipsFake
import build.wallet.ktor.result.HttpError
import build.wallet.nfc.transaction.SignChallengeAndCsek.SignedChallengeAndCsek
import build.wallet.notifications.DeviceTokenManagerError.NoDeviceToken
import build.wallet.notifications.DeviceTokenManagerMock
import build.wallet.notifications.DeviceTokenManagerResult
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.recovery.*
import build.wallet.recovery.LocalRecoveryAttemptProgress.RotatedSpendingKeys
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery.*
import build.wallet.recovery.RecoveryCanceler.RecoveryCancelerError.F8eCancelDelayNotifyError
import build.wallet.relationships.EndorseTrustedContactsServiceMock
import build.wallet.relationships.RelationshipsServiceMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.CreatingSpendingKeysData.AwaitingHardwareProofOfPossessionData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.CreatingSpendingKeysData.CreatingSpendingKeysWithF8EData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.*
import build.wallet.time.ClockFake
import build.wallet.time.ControlledDelayer
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

class RecoveryInProgressDataStateMachineImplTests : FunSpec({
  val clock = ClockFake()
  val lostAppRecoveryCanceler = RecoveryCancelerMock(turbines::create)
  val csekGenerator = CsekGeneratorMock()
  val csekDao = CsekDaoFake()
  val recoveryAuthCompleter = RecoveryAuthCompleterMock(turbines::create)
  val f8eSpendingKeyRotator = F8eSpendingKeyRotatorMock()
  val uuid = UuidGeneratorFake()
  val recoverySyncer = RecoverySyncerMock(StillRecoveringInitiatedRecoveryMock, turbines::create)
  val recoveryDao = RecoveryDaoMock(turbines::create)
  val accountAuthorizer = AccountAuthenticatorMock(turbines::create)
  val deviceTokenManager = DeviceTokenManagerMock(turbines::create)
  val relationshipsService = RelationshipsServiceMock(turbines::create)
  val trustedContactKeyAuthenticator = EndorseTrustedContactsServiceMock(turbines::create)

  val stateMachine =
    RecoveryInProgressDataStateMachineImpl(
      recoveryCanceler = lostAppRecoveryCanceler,
      clock = clock,
      csekGenerator = csekGenerator,
      csekDao = csekDao,
      recoveryAuthCompleter = recoveryAuthCompleter,
      f8eSpendingKeyRotator = f8eSpendingKeyRotator,
      uuidGenerator = uuid,
      recoverySyncer = recoverySyncer,
      accountAuthenticator = accountAuthorizer,
      recoveryDao = recoveryDao,
      delayer = ControlledDelayer(),
      deviceTokenManager = deviceTokenManager,
      relationshipsService = relationshipsService,
      endorseTrustedContactsService = trustedContactKeyAuthenticator
    )

  beforeTest {
    clock.reset()
    csekDao.reset()
    deviceTokenManager.reset()
    relationshipsService.relationshipsFlow.emit(RelationshipsFake)
  }

  fun recovery(delayStartTime: Instant = clock.now) =
    StillRecoveringInitiatedRecoveryMock.copy(
      factorToRecover = App,
      serverRecovery =
        StillRecoveringInitiatedRecoveryMock.serverRecovery.copy(
          delayStartTime = delayStartTime,
          delayEndTime = delayStartTime + 2.minutes
        )
    )

  val onRetryCloudRecoveryCalls = turbines.create<Unit>("on retry cloud recovery calls")

  fun props(recovery: StillRecovering = recovery()) =
    RecoveryInProgressProps(
      fullAccountConfig = FullAccountConfigMock,
      recovery = recovery,
      oldAppGlobalAuthKey = null,
      onRetryCloudRecovery =
        when (recovery.factorToRecover) {
          App -> {
            { onRetryCloudRecoveryCalls += Unit }
          }
          Hardware -> null
        }
    )

  test("recovery is ready to complete") {
    val recovery = recovery()
    // Move clock ahead of delay period
    clock.advanceBy(2.minutes)
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
      }
    }
  }

  // TODO(W-3249): test is not working because we don't know how to manage `delay` in tests.
  xtest("delay period is pending, advance by 1 minute, still pending") {
    val recovery = recovery()
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<WaitingForRecoveryDelayPeriodData>()
        it.factorToRecover.shouldBe(App)
        it.delayPeriodStartTime.shouldBe(recovery.serverRecovery.delayStartTime)
        it.delayPeriodEndTime.shouldBe(recovery.serverRecovery.delayEndTime)
      }

      // Move clock but not ahead of delay period end time
      testCoroutineScheduler.advanceTimeBy(1.minutes)

      expectNoEvents()
    }
  }

  // TODO(W-3249): test is not working because we don't know how to manage `delay` in tests.
  xtest("delay period is pending, advance by 2 minutes, recovery ready to complete") {
    val recovery = recovery()
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<WaitingForRecoveryDelayPeriodData>()
        it.factorToRecover.shouldBe(App)
        it.delayPeriodStartTime.shouldBe(recovery.serverRecovery.delayStartTime)
        it.delayPeriodEndTime.shouldBe(recovery.serverRecovery.delayEndTime)
      }

      // Move clock ahead of delay period end time
      testCoroutineScheduler.advanceTimeBy(2.minutes)

      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
      }
    }
  }

  test("cancel recovery while delay period is pending") {
    val recovery = recovery()
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<WaitingForRecoveryDelayPeriodData>()
        it.delayPeriodStartTime.shouldBe(recovery.serverRecovery.delayStartTime)
        it.delayPeriodEndTime.shouldBe(recovery.serverRecovery.delayEndTime)
        it.cancel()
      }

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofOfPossessionForCancellationData>()
        it.addHardwareProofOfPossession(HwFactorProofOfPossession(""))
      }

      lostAppRecoveryCanceler.cancelCalls.awaitItem()

      awaitItem().shouldBeTypeOf<CancellingData>()
    }
  }

  test("cancel hw recovery while delay period is pending") {
    val recovery = recovery()
    stateMachine.test(
      props(recovery.copy(factorToRecover = Hardware))
    ) {
      awaitItem().let {
        it.shouldBeTypeOf<WaitingForRecoveryDelayPeriodData>()
        it.delayPeriodStartTime.shouldBe(recovery.serverRecovery.delayStartTime)
        it.delayPeriodEndTime.shouldBe(recovery.serverRecovery.delayEndTime)
        it.cancel()
      }

      lostAppRecoveryCanceler.cancelCalls.awaitItem()

      awaitItem().shouldBeTypeOf<CancellingData>()
    }
  }

  test("cancel hw recovery when it is ready to be completed") {
    val recovery = recovery().copy(factorToRecover = Hardware)
    // Move clock ahead of delay period end time
    clock.advanceBy(2.minutes)
    stateMachine.test(
      props(recovery)
    ) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.cancel()
      }

      awaitItem().shouldBeTypeOf<CancellingData>()

      lostAppRecoveryCanceler.cancelCalls.awaitItem()
    }
  }

  test("attempt to cancel recovery but it fails ") {
    val recovery = recovery()
    // Move clock ahead of delay period end time
    clock.advanceBy(2.minutes)
    lostAppRecoveryCanceler.result =
      Err(
        F8eCancelDelayNotifyError(F8eError.UnhandledException(HttpError.UnhandledException(Throwable())))
      )

    stateMachine.test(props(recovery)) {

      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.cancel()
      }

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofOfPossessionForCancellationData>()
        it.addHardwareProofOfPossession(HwFactorProofOfPossession(""))
      }

      awaitItem().let {
        it.shouldBeTypeOf<CancellingData>()
      }

      awaitItem().let {
        it.shouldBeTypeOf<FailedToCancelRecoveryData>()
        it.onAcknowledge()
      }

      awaitItem().shouldBeTypeOf<ReadyToCompleteRecoveryData>()

      lostAppRecoveryCanceler.cancelCalls.awaitItem()
    }
  }

  test("cancel recovery when it is ready to be completed and requires notification comms") {
    val recovery = recovery()
    // Move clock ahead of delay period end time
    clock.advanceBy(2.minutes)
    lostAppRecoveryCanceler.result =
      Err(
        F8eCancelDelayNotifyError(
          SpecificClientErrorMock(CancelDelayNotifyRecoveryErrorCode.COMMS_VERIFICATION_REQUIRED)
        )
      )
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.cancel()
      }

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofOfPossessionForCancellationData>()
        it.addHardwareProofOfPossession(HwFactorProofOfPossession(""))
      }

      lostAppRecoveryCanceler.cancelCalls.awaitItem()

      awaitItem().shouldBeTypeOf<CancellingData>()

      with(awaitItem()) {
        shouldBeTypeOf<VerifyingNotificationCommsForCancellationData>()
        lostAppRecoveryCanceler.result = Ok(Unit)
        onComplete()
      }

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofOfPossessionForCancellationData>()
        it.addHardwareProofOfPossession(HwFactorProofOfPossession(""))
      }

      lostAppRecoveryCanceler.cancelCalls.awaitItem()

      awaitItem().shouldBeTypeOf<CancellingData>()
    }
  }

  test("cancel hw recovery when it is ready to be completed and requires notification comms") {
    val recovery = recovery().copy(factorToRecover = Hardware)
    // Move clock ahead of delay period end time
    clock.advanceBy(2.minutes)
    lostAppRecoveryCanceler.result =
      Err(
        F8eCancelDelayNotifyError(
          SpecificClientErrorMock(CancelDelayNotifyRecoveryErrorCode.COMMS_VERIFICATION_REQUIRED)
        )
      )
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.cancel()
      }

      lostAppRecoveryCanceler.cancelCalls.awaitItem()

      awaitItem().shouldBeTypeOf<CancellingData>()

      with(awaitItem()) {
        shouldBeTypeOf<VerifyingNotificationCommsForCancellationData>()
        lostAppRecoveryCanceler.result = Ok(Unit)
        onComplete()
      }

      lostAppRecoveryCanceler.cancelCalls.awaitItem()

      awaitItem().shouldBeTypeOf<CancellingData>()
    }
  }

  test("rollback instead of signing challenge and csek") {
    val recovery = recovery()
    // Move clock ahead of delay period
    clock.advanceBy(2.minutes)
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Rotate auth keys
      recoveryAuthCompleter.rotateAuthKeysResult = Ok(Unit)
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()

        csekDao.setResult = Err(IllegalStateException())
        it.nfcTransaction.onCancel()
      }

      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
      }
    }
  }

  test("csekDao set failure") {
    val recovery = recovery()
    // Move clock ahead of delay period
    clock.advanceBy(2.minutes)
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Rotate auth keys
      recoveryAuthCompleter.rotateAuthKeysResult = Ok(Unit)
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        csekDao.setResult = Err(IllegalStateException())
        it.nfcTransaction.onSuccess(
          SignedChallengeAndCsek(
            signedChallenge = "",
            sealedCsek = SealedCsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<FailedToRotateAuthData>()
      }
    }
  }

  test("complete recovery with socrec") {
    val recovery = recovery()
    // Move clock ahead of delay period
    clock.advanceBy(2.minutes)
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Rotate auth keys
      recoveryAuthCompleter.rotateAuthKeysResult = Ok(Unit)
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndCsek(
            signedChallenge = "",
            sealedCsek = SealedCsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
        recoveryAuthCompleter.rotateAuthKeysCalls.awaitItem()
      }

      updateProps(
        props(
          RotatedAuthKeys(
            fullAccountId = recovery.fullAccountId,
            appSpendingKey = recovery.appSpendingKey,
            appGlobalAuthKey = recovery.appGlobalAuthKey,
            appRecoveryAuthKey = recovery.appRecoveryAuthKey,
            hardwareSpendingKey = recovery.hardwareSpendingKey,
            appGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature,
            hardwareAuthKey = recovery.hardwareAuthKey,
            factorToRecover = recovery.factorToRecover,
            sealedCsek = SealedCsekFake
          )
        )
      )

      // Rotate spending keys
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().let {
        it.shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
        recoverySyncer.setLocalRecoveryProgressCalls.awaitItem()
          .shouldBeTypeOf<RotatedSpendingKeys>()
      }

      updateProps(
        props(
          CreatedSpendingKeys(
            fullAccountId = recovery.fullAccountId,
            appSpendingKey = recovery.appSpendingKey,
            appGlobalAuthKey = recovery.appGlobalAuthKey,
            appRecoveryAuthKey = recovery.appRecoveryAuthKey,
            hardwareSpendingKey = recovery.hardwareSpendingKey,
            appGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature,
            hardwareAuthKey = recovery.hardwareAuthKey,
            factorToRecover = recovery.factorToRecover,
            f8eSpendingKeyset = F8eSpendingKeysetMock,
            sealedCsek = SealedCsekFake
          )
        )
      )

      // Generating TC certs with new auth keys
      awaitItem().shouldBe(RegeneratingTcCertificatesData)
      relationshipsService.syncCalls.awaitItem()

      // Backing up new keybox
      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
        it.sealedCsek.shouldBe(SealedCsekFake)
        it.onBackupFinished()
      }

      recoverySyncer.setLocalRecoveryProgressCalls.awaitItem()

      deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()

      updateProps(
        props(
          BackedUpToCloud(
            fullAccountId = recovery.fullAccountId,
            appSpendingKey = recovery.appSpendingKey,
            appGlobalAuthKey = recovery.appGlobalAuthKey,
            appRecoveryAuthKey = recovery.appRecoveryAuthKey,
            hardwareSpendingKey = recovery.hardwareSpendingKey,
            appGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature,
            hardwareAuthKey = recovery.hardwareAuthKey,
            factorToRecover = recovery.factorToRecover,
            f8eSpendingKeyset = F8eSpendingKeysetMock
          )
        )
      )

      // Sweeping funds
      awaitItem().let {
        it.shouldBeTypeOf<PerformingSweepData>()
      }
    }
  }

  test("complete recovery") {
    val recovery = recovery()
    // Move clock ahead of delay period
    clock.advanceBy(2.minutes)
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Rotate auth keys
      recoveryAuthCompleter.rotateAuthKeysResult = Ok(Unit)
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndCsek(
            signedChallenge = "",
            sealedCsek = SealedCsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
        recoveryAuthCompleter.rotateAuthKeysCalls.awaitItem()
      }

      updateProps(
        props(
          RotatedAuthKeys(
            fullAccountId = recovery.fullAccountId,
            appSpendingKey = recovery.appSpendingKey,
            appGlobalAuthKey = recovery.appGlobalAuthKey,
            appRecoveryAuthKey = recovery.appRecoveryAuthKey,
            hardwareSpendingKey = recovery.hardwareSpendingKey,
            hardwareAuthKey = recovery.hardwareAuthKey,
            appGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature,
            factorToRecover = recovery.factorToRecover,
            sealedCsek = SealedCsekFake
          )
        )
      )

      // Rotate spending keys
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().let {
        it.shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
        recoverySyncer.setLocalRecoveryProgressCalls.awaitItem()
          .shouldBeTypeOf<RotatedSpendingKeys>()
      }

      updateProps(
        props(
          CreatedSpendingKeys(
            fullAccountId = recovery.fullAccountId,
            appSpendingKey = recovery.appSpendingKey,
            appGlobalAuthKey = recovery.appGlobalAuthKey,
            appRecoveryAuthKey = recovery.appRecoveryAuthKey,
            hardwareSpendingKey = recovery.hardwareSpendingKey,
            hardwareAuthKey = recovery.hardwareAuthKey,
            factorToRecover = recovery.factorToRecover,
            appGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature,
            f8eSpendingKeyset = F8eSpendingKeysetMock,
            sealedCsek = SealedCsekFake
          )
        )
      )

      // Generating TC certs with new auth keys
      awaitItem().shouldBe(RegeneratingTcCertificatesData)
      relationshipsService.syncCalls.awaitItem()

      // Backing up new keybox
      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
        it.sealedCsek.shouldBe(SealedCsekFake)
        it.onBackupFinished()
      }

      recoverySyncer.setLocalRecoveryProgressCalls.awaitItem()

      deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()

      updateProps(
        props(
          BackedUpToCloud(
            fullAccountId = recovery.fullAccountId,
            appSpendingKey = recovery.appSpendingKey,
            appGlobalAuthKey = recovery.appGlobalAuthKey,
            appRecoveryAuthKey = recovery.appRecoveryAuthKey,
            hardwareSpendingKey = recovery.hardwareSpendingKey,
            appGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature,
            hardwareAuthKey = recovery.hardwareAuthKey,
            factorToRecover = recovery.factorToRecover,
            f8eSpendingKeyset = F8eSpendingKeysetMock
          )
        )
      )

      // Sweeping funds
      awaitItem().let {
        it.shouldBeTypeOf<PerformingSweepData>()
      }
    }
  }

  test("exit and restart sweep") {
    val recovery = recovery()
    // Move clock ahead of delay period
    clock.advanceBy(2.minutes)
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Rotate auth keys
      recoveryAuthCompleter.rotateAuthKeysResult = Ok(Unit)
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndCsek(
            signedChallenge = "",
            sealedCsek = SealedCsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
        recoveryAuthCompleter.rotateAuthKeysCalls.awaitItem()
      }

      updateProps(
        props(
          RotatedAuthKeys(
            fullAccountId = recovery.fullAccountId,
            appSpendingKey = recovery.appSpendingKey,
            appGlobalAuthKey = recovery.appGlobalAuthKey,
            appRecoveryAuthKey = recovery.appRecoveryAuthKey,
            hardwareSpendingKey = recovery.hardwareSpendingKey,
            appGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature,
            hardwareAuthKey = recovery.hardwareAuthKey,
            factorToRecover = recovery.factorToRecover,
            sealedCsek = SealedCsekFake
          )
        )
      )

      // Rotate spending keys
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().let {
        it.shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
        recoverySyncer.setLocalRecoveryProgressCalls.awaitItem()
      }

      updateProps(
        props(
          CreatedSpendingKeys(
            fullAccountId = recovery.fullAccountId,
            appSpendingKey = recovery.appSpendingKey,
            appGlobalAuthKey = recovery.appGlobalAuthKey,
            appRecoveryAuthKey = recovery.appRecoveryAuthKey,
            hardwareSpendingKey = recovery.hardwareSpendingKey,
            hardwareAuthKey = recovery.hardwareAuthKey,
            appGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature,
            factorToRecover = recovery.factorToRecover,
            f8eSpendingKeyset = F8eSpendingKeysetMock,
            sealedCsek = SealedCsekFake
          )
        )
      )

      // Generating TC certs with new auth keys
      awaitItem().shouldBe(RegeneratingTcCertificatesData)
      relationshipsService.syncCalls.awaitItem()

      // Backing up new keybox
      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
        it.sealedCsek.shouldBe(SealedCsekFake)
        it.onBackupFinished()
      }

      recoverySyncer.setLocalRecoveryProgressCalls.awaitItem()

      deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()

      updateProps(
        props(
          BackedUpToCloud(
            fullAccountId = recovery.fullAccountId,
            appSpendingKey = recovery.appSpendingKey,
            appGlobalAuthKey = recovery.appGlobalAuthKey,
            appRecoveryAuthKey = recovery.appRecoveryAuthKey,
            hardwareSpendingKey = recovery.hardwareSpendingKey,
            hardwareAuthKey = recovery.hardwareAuthKey,
            appGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature,
            factorToRecover = recovery.factorToRecover,
            f8eSpendingKeyset = F8eSpendingKeysetMock
          )
        )
      )

      // Sweeping funds
      awaitItem().let {
        it.shouldBeTypeOf<PerformingSweepData>()
        it.rollback()
      }

      awaitItem().let {
        it.shouldBeTypeOf<ExitedPerformingSweepData>()
        it.retry()
      }

      awaitItem().let {
        it.shouldBeTypeOf<PerformingSweepData>()
      }
    }
  }

  test("fail and retry cloud backup") {
    val recovery = recovery()
    // Move clock ahead of delay period
    clock.advanceBy(2.minutes)
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Rotate auth keys
      recoveryAuthCompleter.rotateAuthKeysResult = Ok(Unit)
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndCsek(
            signedChallenge = "",
            sealedCsek = SealedCsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
        recoveryAuthCompleter.rotateAuthKeysCalls.awaitItem()
      }

      updateProps(
        props(
          RotatedAuthKeys(
            fullAccountId = recovery.fullAccountId,
            appSpendingKey = recovery.appSpendingKey,
            appGlobalAuthKey = recovery.appGlobalAuthKey,
            appRecoveryAuthKey = recovery.appRecoveryAuthKey,
            hardwareSpendingKey = recovery.hardwareSpendingKey,
            hardwareAuthKey = recovery.hardwareAuthKey,
            appGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature,
            factorToRecover = recovery.factorToRecover,
            sealedCsek = SealedCsekFake
          )
        )
      )

      // Rotate spending keys
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().let {
        it.shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      }

      recoverySyncer.setLocalRecoveryProgressCalls.awaitItem()

      deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()

      updateProps(
        props(
          CreatedSpendingKeys(
            fullAccountId = recovery.fullAccountId,
            appSpendingKey = recovery.appSpendingKey,
            appGlobalAuthKey = recovery.appGlobalAuthKey,
            appRecoveryAuthKey = recovery.appRecoveryAuthKey,
            hardwareSpendingKey = recovery.hardwareSpendingKey,
            hardwareAuthKey = recovery.hardwareAuthKey,
            factorToRecover = recovery.factorToRecover,
            appGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature,
            f8eSpendingKeyset = F8eSpendingKeysetMock,
            sealedCsek = SealedCsekFake
          )
        )
      )

      // Generating TC certs with new auth keys
      awaitItem().shouldBe(RegeneratingTcCertificatesData)
      relationshipsService.syncCalls.awaitItem()

      // Backing up new keybox
      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
        it.sealedCsek.shouldBe(SealedCsekFake)
        it.onBackupFailed(Error())
      }

      // Retrying
      awaitItem().let {
        it.shouldBeTypeOf<FailedPerformingCloudBackupData>()
        it.retry()
      }

      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
      }
    }
  }

  test("ignore failure adding device token") {
    val recovery = recovery()
    deviceTokenManager.addDeviceTokenIfPresentForAccountReturn =
      DeviceTokenManagerResult.Err(NoDeviceToken)
    // Move clock ahead of delay period
    clock.advanceBy(2.minutes)
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Rotate auth keys
      recoveryAuthCompleter.rotateAuthKeysResult = Ok(Unit)
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndCsek(
            signedChallenge = "",
            sealedCsek = SealedCsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
        recoveryAuthCompleter.rotateAuthKeysCalls.awaitItem()
      }

      updateProps(
        props(
          RotatedAuthKeys(
            fullAccountId = recovery.fullAccountId,
            appSpendingKey = recovery.appSpendingKey,
            appGlobalAuthKey = recovery.appGlobalAuthKey,
            appRecoveryAuthKey = recovery.appRecoveryAuthKey,
            hardwareSpendingKey = recovery.hardwareSpendingKey,
            hardwareAuthKey = recovery.hardwareAuthKey,
            factorToRecover = recovery.factorToRecover,
            appGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature,
            sealedCsek = SealedCsekFake
          )
        )
      )

      // Rotate spending keys
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().let {
        it.shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      }

      updateProps(
        props(
          CreatedSpendingKeys(
            fullAccountId = recovery.fullAccountId,
            appSpendingKey = recovery.appSpendingKey,
            appGlobalAuthKey = recovery.appGlobalAuthKey,
            appRecoveryAuthKey = recovery.appRecoveryAuthKey,
            hardwareSpendingKey = recovery.hardwareSpendingKey,
            hardwareAuthKey = recovery.hardwareAuthKey,
            appGlobalAuthKeyHwSignature = recovery.appGlobalAuthKeyHwSignature,
            factorToRecover = recovery.factorToRecover,
            f8eSpendingKeyset = F8eSpendingKeysetMock,
            sealedCsek = SealedCsekFake
          )
        )
      )

      // Generating TC certs with new auth keys
      awaitItem().shouldBe(RegeneratingTcCertificatesData)
      relationshipsService.syncCalls.awaitItem()

      // Backing up new keybox
      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
        it.sealedCsek.shouldBe(SealedCsekFake)
      }

      recoverySyncer.setLocalRecoveryProgressCalls.awaitItem()

      deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
    }
  }
})
