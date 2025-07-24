@file:OptIn(DelicateCoroutinesApi::class)

package build.wallet.statemachine.data.recovery.inprogress

import app.cash.turbine.plusAssign
import bitkey.account.AccountConfigServiceFake
import bitkey.backup.DescriptorBackup
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.SpecificClientErrorMock
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import bitkey.recovery.DescriptorBackupError
import bitkey.recovery.DescriptorBackupPreparedData
import build.wallet.auth.AccountAuthenticatorMock
import build.wallet.bitkey.challange.DelayNotifyRecoveryChallengeFake
import build.wallet.bitkey.challange.SignedChallenge
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.csek.CsekDaoFake
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.cloud.backup.csek.SekGeneratorMock
import build.wallet.cloud.backup.csek.SsekDaoFake
import build.wallet.cloud.backup.csek.SsekFake
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.relationships.RelationshipsFake
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.feature.flags.EncryptedDescriptorBackupsFeatureFlag
import build.wallet.ktor.result.HttpError
import build.wallet.nfc.transaction.SealDelegatedDecryptionKey
import build.wallet.nfc.transaction.SignChallengeAndSealSeks.SignedChallengeAndSeks
import build.wallet.nfc.transaction.UnsealData
import build.wallet.notifications.DeviceTokenManagerError.NoDeviceToken
import build.wallet.notifications.DeviceTokenManagerMock
import build.wallet.notifications.DeviceTokenManagerResult
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.recovery.*
import build.wallet.recovery.CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError
import build.wallet.recovery.DescriptorBackupServiceFake
import build.wallet.recovery.LocalRecoveryAttemptProgress.RotatedSpendingKeys
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery.*
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery.UploadedDescriptorBackups
import build.wallet.relationships.*
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.CreatingSpendingKeysData.AwaitingHardwareProofOfPossessionData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.CreatingSpendingKeysData.CreatingSpendingKeysWithF8EData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.ProcessingDescriptorBackupsData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.*
import build.wallet.time.ClockFake
import build.wallet.time.MinimumLoadingDuration
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.ktor.util.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okio.ByteString.Companion.decodeBase64
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RecoveryInProgressDataStateMachineImplTests : FunSpec({
  val clock = ClockFake()
  val lostHardwareRecoveryService = LostHardwareRecoveryServiceFake()
  val lostAppAndCloudRecoveryService = LostAppAndCloudRecoveryServiceFake()
  val sekGenerator = SekGeneratorMock()
  val csekDao = CsekDaoFake()
  val ssekDao = SsekDaoFake()
  val recoveryAuthCompleter = RecoveryAuthCompleterMock(turbines::create)
  val f8eSpendingKeyRotator = F8eSpendingKeyRotatorMock()
  val uuid = UuidGeneratorFake()
  val recoveryStatusService =
    RecoveryStatusServiceMock(StillRecoveringInitiatedRecoveryMock, turbines::create)
  val accountAuthorizer = AccountAuthenticatorMock(turbines::create)
  val deviceTokenManager = DeviceTokenManagerMock(turbines::create)
  val relationshipsService = RelationshipsServiceMock(turbines::create, clock)
  val relationshipsKeysRepository = RelationshipsKeysRepository(
    relationshipsCrypto = RelationshipsCryptoFake(),
    relationshipKeysDao = RelationshipsKeysDaoFake()
  )
  val accountConfigService = AccountConfigServiceFake()
  val trustedContactKeyAuthenticator = EndorseTrustedContactsServiceMock(turbines::create)
  val descriptorBackupService = DescriptorBackupServiceFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val encryptedDescriptorBackupsFeatureFlag = EncryptedDescriptorBackupsFeatureFlag(featureFlagDao)
  val fakeChallenge = SignedChallenge.HardwareSignedChallenge(
    challenge = DelayNotifyRecoveryChallengeFake,
    signature = ""
  )
  val delegatedDecryptionKeyService = DelegatedDecryptionKeyServiceMock(
    uploadCalls = turbines.create("upload calls")
  )

  val stateMachine = RecoveryInProgressDataStateMachineImpl(
    lostHardwareRecoveryService = lostHardwareRecoveryService,
    lostAppAndCloudRecoveryService = lostAppAndCloudRecoveryService,
    clock = Clock.System,
    sekGenerator = sekGenerator,
    csekDao = csekDao,
    ssekDao = ssekDao,
    recoveryAuthCompleter = recoveryAuthCompleter,
    f8eSpendingKeyRotator = f8eSpendingKeyRotator,
    uuidGenerator = uuid,
    recoveryStatusService = recoveryStatusService,
    accountAuthenticator = accountAuthorizer,
    deviceTokenManager = deviceTokenManager,
    relationshipsService = relationshipsService,
    endorseTrustedContactsService = trustedContactKeyAuthenticator,
    delegatedDecryptionKeyService = delegatedDecryptionKeyService,
    relationshipsKeysRepository = relationshipsKeysRepository,
    minimumLoadingDuration = MinimumLoadingDuration(0.seconds),
    accountConfigService = accountConfigService,
    descriptorBackupService = descriptorBackupService,
    encryptedDescriptorBackupsFeatureFlag = encryptedDescriptorBackupsFeatureFlag
  )

  beforeTest {
    csekDao.reset()
    ssekDao.reset()
    deviceTokenManager.reset()
    relationshipsService.relationshipsFlow.emit(RelationshipsFake)
    accountConfigService.reset()
    lostAppAndCloudRecoveryService.reset()
    descriptorBackupService.reset()
    featureFlagDao.reset()
    encryptedDescriptorBackupsFeatureFlag.reset()

    encryptedDescriptorBackupsFeatureFlag.setFlagValue(BooleanFlag(true))
  }

  val delayDuration = 100.milliseconds

  fun recovery(delayStartTime: Instant = Clock.System.now()) =
    StillRecoveringInitiatedRecoveryMock.copy(
      factorToRecover = App,
      serverRecovery =
        StillRecoveringInitiatedRecoveryMock.serverRecovery.copy(
          delayStartTime = delayStartTime,
          delayEndTime = delayStartTime + delayDuration
        )
    )

  fun hardwareRecovery(delayStartTime: Instant = Clock.System.now()) =
    StillRecoveringInitiatedRecoveryMock.copy(
      factorToRecover = Hardware,
      serverRecovery =
        StillRecoveringInitiatedRecoveryMock.serverRecovery.copy(
          delayStartTime = delayStartTime,
          delayEndTime = delayStartTime + delayDuration
        )
    )

  val onRetryCloudRecoveryCalls = turbines.create<Unit>("on retry cloud recovery calls")

  fun props(recovery: StillRecovering = recovery()) =
    RecoveryInProgressProps(
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
    delay(delayDuration)
    stateMachine.test(
      props = props(recovery),
      testTimeout = 20.seconds,
      turbineTimeout = 10.seconds
    ) {
      awaitItem().shouldBeTypeOf<ReadyToCompleteRecoveryData>()
    }
  }

  test("delay period is pending, wait for part of the delay duration, still pending") {
    val recovery = recovery()
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<WaitingForRecoveryDelayPeriodData>()
        it.factorToRecover.shouldBe(App)
        it.delayPeriodStartTime.shouldBe(recovery.serverRecovery.delayStartTime)
        it.delayPeriodEndTime.shouldBe(recovery.serverRecovery.delayEndTime)
      }

      // Move clock but not ahead of delay period end time, still pending
      awaitNoEvents(timeout = 10.milliseconds)

      // Ready later
      awaitItem().shouldBeTypeOf<ReadyToCompleteRecoveryData>()
    }
  }

  test("delay period is pending, wait for delay to complete, recovery ready to complete") {
    val recovery = recovery()
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<WaitingForRecoveryDelayPeriodData>()
        it.factorToRecover.shouldBe(App)
        it.delayPeriodStartTime.shouldBe(recovery.serverRecovery.delayStartTime)
        it.delayPeriodEndTime.shouldBe(recovery.serverRecovery.delayEndTime)
      }

      awaitItem().shouldBeTypeOf<ReadyToCompleteRecoveryData>()
    }
  }

  test("cancel lost app and cloud recovery while delay period is pending") {
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

      awaitItem().shouldBeTypeOf<CancellingData>()
    }
  }

  test("cancel lost hw recovery while delay period is pending") {
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

      awaitItem().shouldBeTypeOf<CancellingData>()
    }
  }

  test("cancel lost hw recovery when it is ready to be completed") {
    val recovery = recovery().copy(factorToRecover = Hardware)
    // Move clock ahead of delay period end time
    delay(delayDuration)
    stateMachine.test(
      props(recovery)
    ) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.cancel()
      }

      awaitItem().shouldBeTypeOf<CancellingData>()
    }
  }

  test("attempt to cancel lost app and cloud recovery but it fails ") {
    val recovery = recovery()
    // Move clock ahead of delay period end time
    delay(delayDuration)
    lostAppAndCloudRecoveryService.cancelResult =
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

      awaitItem().shouldBeTypeOf<CancellingData>()

      awaitItem().let {
        it.shouldBeTypeOf<FailedToCancelRecoveryData>()
        it.onAcknowledge()
      }

      awaitItem().shouldBeTypeOf<ReadyToCompleteRecoveryData>()
    }
  }

  test("cancel lost app and cloud recovery when it is ready to be completed and requires notification comms") {
    val recovery = recovery()
    // Move clock ahead of delay period end time
    delay(delayDuration)
    lostAppAndCloudRecoveryService.cancelResult =
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

      awaitItem().shouldBeTypeOf<CancellingData>()

      with(awaitItem()) {
        shouldBeTypeOf<VerifyingNotificationCommsForCancellationData>()
        lostAppAndCloudRecoveryService.reset()
        onComplete()
      }

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofOfPossessionForCancellationData>()
        it.addHardwareProofOfPossession(HwFactorProofOfPossession(""))
      }

      awaitItem().shouldBeTypeOf<CancellingData>()
    }
  }

  test("cancel lost hw recovery when it is ready to be completed and requires notification comms") {
    val recovery = recovery().copy(factorToRecover = Hardware)
    // Move clock ahead of delay period end time
    delay(delayDuration)
    lostHardwareRecoveryService.cancelResult =
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

      awaitItem().shouldBeTypeOf<CancellingData>()

      with(awaitItem()) {
        shouldBeTypeOf<VerifyingNotificationCommsForCancellationData>()
        lostHardwareRecoveryService.reset()
        onComplete()
      }

      awaitItem().shouldBeTypeOf<CancellingData>()
    }
  }

  test("rollback instead of signing challenge and csek") {
    val recovery = recovery()
    // Move clock ahead of delay period
    delay(delayDuration)
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

      awaitItem().shouldBeTypeOf<ReadyToCompleteRecoveryData>()
    }
  }

  test("csekDao set failure") {
    val recovery = recovery()
    // Move clock ahead of delay period
    delay(delayDuration)
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
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().shouldBeTypeOf<FailedToRotateAuthData>()
    }
  }

  test("complete recovery with socrec - descriptor backups disabled") {
    val recovery = recovery()

    accountAuthorizer.authResults = mutableListOf(accountAuthorizer.defaultErrorAuthResult)
    // Explicitly disable descriptor backups feature flag to test fallback behavior
    encryptedDescriptorBackupsFeatureFlag.setFlagValue(BooleanFlag(false))

    // Move clock ahead of delay period
    delay(delayDuration)

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
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
        accountAuthorizer.authCalls.awaitItem()
        recoveryAuthCompleter.rotateAuthKeysCalls.awaitItem()
      }
      awaitItem().shouldBeTypeOf<FetchingSealedDelegatedDecryptionKeyFromF8eData>()

      awaitItem().let {
        it.shouldBeTypeOf<FetchingSealedDelegatedDecryptionKeyStringData>()
        it.nfcTransaction.onSuccess(
          UnsealData.UnsealedDataResult("unsealed-data".encodeBase64().decodeBase64()!!)
        )
      }

      // Rotate spending keys
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().let {
        it.shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
        recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
          .shouldBeTypeOf<RotatedSpendingKeys>()
          .f8eSpendingKeyset.shouldBe(F8eSpendingKeysetMock)
      }

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)
      relationshipsService.syncCalls.awaitItem()

      // Backing up new keybox
      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
        it.sealedCsek.shouldBe(SealedCsekFake)
        it.onBackupFinished()
      }

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.BackedUpToCloud>()
      deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()

      awaitItem().shouldBeTypeOf<PerformingSweepData>()
    }
  }

  test("complete recovery with socrec - descriptor backups enabled") {
    val recovery = recovery()

    accountAuthorizer.authResults = mutableListOf(accountAuthorizer.defaultErrorAuthResult)

    // Move clock ahead of delay period
    delay(delayDuration)

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
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
        accountAuthorizer.authCalls.awaitItem()
        recoveryAuthCompleter.rotateAuthKeysCalls.awaitItem()
      }

      awaitItem().shouldBeTypeOf<FetchingSealedDelegatedDecryptionKeyFromF8eData>()

      awaitItem().let {
        it.shouldBeTypeOf<FetchingSealedDelegatedDecryptionKeyStringData>()
        it.nfcTransaction.onSuccess(
          UnsealData.UnsealedDataResult("unsealed-data".encodeBase64().decodeBase64()!!)
        )
      }

      // Rotate spending keys
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().let {
        it.shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
        recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
          .shouldBeTypeOf<RotatedSpendingKeys>()
          .f8eSpendingKeyset.shouldBe(F8eSpendingKeysetMock)
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()
      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()
        .spendingKeysets.shouldBe(listOf(SpendingKeysetMock))

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)
      relationshipsService.syncCalls.awaitItem()

      // Backing up new keybox
      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
        it.sealedCsek.shouldBe(SealedCsekFake)
        it.onBackupFinished()
      }

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.BackedUpToCloud>()
      deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()

      // Sweeping funds
      awaitItem().shouldBeTypeOf<PerformingSweepData>()
    }
  }

  test("complete recovery") {
    val recovery = recovery()
    // Move clock ahead of delay period
    delay(delayDuration)
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Rotate auth keys
      accountAuthorizer.authResults = mutableListOf(accountAuthorizer.defaultErrorAuthResult)
      recoveryAuthCompleter.rotateAuthKeysResult = Ok(Unit)
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
        accountAuthorizer.authCalls.awaitItem()
        recoveryAuthCompleter.rotateAuthKeysCalls.awaitItem()
      }

      awaitItem().shouldBeTypeOf<FetchingSealedDelegatedDecryptionKeyFromF8eData>()

      awaitItem().let {
        it.shouldBeTypeOf<FetchingSealedDelegatedDecryptionKeyStringData>()
        it.nfcTransaction.onSuccess(
          UnsealData.UnsealedDataResult("unsealed-data".encodeBase64().decodeBase64()!!)
        )
      }

      // Rotate spending keys
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().let {
        it.shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
        recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
          .shouldBeTypeOf<RotatedSpendingKeys>()
          .f8eSpendingKeyset.shouldBe(F8eSpendingKeysetMock)
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)
      relationshipsService.syncCalls.awaitItem()

      // Backing up new keybox
      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
        it.sealedCsek.shouldBe(SealedCsekFake)
        it.onBackupFinished()
      }

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.BackedUpToCloud>()

      deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()

      awaitItem().shouldBeTypeOf<PerformingSweepData>()
    }
  }

  test("complete hardware recovery") {
    val recovery = hardwareRecovery()
    delay(delayDuration)
    stateMachine.test(
      props = props(recovery),
      turbineTimeout = 10.seconds
    ) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Rotate auth keys
      accountAuthorizer.authResults = mutableListOf(accountAuthorizer.defaultErrorAuthResult)
      recoveryAuthCompleter.rotateAuthKeysResult = Ok(Unit)
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
        accountAuthorizer.authCalls.awaitItem()
        recoveryAuthCompleter.rotateAuthKeysCalls.awaitItem()
      }

      awaitItem().shouldBeTypeOf<FetchingSealedDelegatedDecryptionKeyFromF8eData>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().let {
        it.shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
        recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
          .shouldBeTypeOf<RotatedSpendingKeys>()
          .f8eSpendingKeyset.shouldBe(F8eSpendingKeysetMock)
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()
        .spendingKeysets.shouldBe(listOf(SpendingKeysetMock))

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()
      awaitItem().let {
        it.shouldBeTypeOf<SealingDelegatedDecryptionKeyData>()
        it.nfcTransaction.onSuccess(
          SealDelegatedDecryptionKey.SealedDataResult(
            "FakeSealedData".encodeBase64().decodeBase64()!!
          )
        )
        delegatedDecryptionKeyService.uploadCalls!!.awaitItem()
      }
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)
      relationshipsService.syncCalls.awaitItem()

      // Backing up new keybox
      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
        it.sealedCsek.shouldBe(SealedCsekFake)
        it.onBackupFinished()
      }

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
      deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()

      awaitItem().shouldBeTypeOf<PerformingSweepData>()
    }
  }

  test("exit and restart sweep") {
    val recovery = recovery()
    // Move clock ahead of delay period
    delay(delayDuration)
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Rotate auth keys
      accountAuthorizer.authResults = mutableListOf(accountAuthorizer.defaultErrorAuthResult)
      recoveryAuthCompleter.rotateAuthKeysResult = Ok(Unit)
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
        accountAuthorizer.authCalls.awaitItem()
        recoveryAuthCompleter.rotateAuthKeysCalls.awaitItem()
      }

      awaitItem().shouldBeTypeOf<FetchingSealedDelegatedDecryptionKeyFromF8eData>()

      awaitItem().let {
        it.shouldBeTypeOf<FetchingSealedDelegatedDecryptionKeyStringData>()
        it.nfcTransaction.onSuccess(
          UnsealData.UnsealedDataResult("unsealed-data".encodeBase64().decodeBase64()!!)
        )
      }

      // Rotate spending keys
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().let {
        it.shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
        recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)
      relationshipsService.syncCalls.awaitItem()

      // Backing up new keybox
      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
        it.sealedCsek.shouldBe(SealedCsekFake)
        it.onBackupFinished()
      }

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()

      deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()

      awaitItem().let {
        it.shouldBeTypeOf<PerformingSweepData>()
        it.rollback()
      }

      awaitItem().let {
        it.shouldBeTypeOf<ExitedPerformingSweepData>()
        it.retry()
      }

      awaitItem().shouldBeTypeOf<PerformingSweepData>()
    }
  }

  test("fail and retry cloud backup") {
    val recovery = recovery()
    // Move clock ahead of delay period
    delay(delayDuration)
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Rotate auth keys
      accountAuthorizer.authResults = mutableListOf(accountAuthorizer.defaultErrorAuthResult)
      recoveryAuthCompleter.rotateAuthKeysResult = Ok(Unit)
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
        accountAuthorizer.authCalls.awaitItem()
        recoveryAuthCompleter.rotateAuthKeysCalls.awaitItem()
      }

      awaitItem().shouldBeTypeOf<FetchingSealedDelegatedDecryptionKeyFromF8eData>()

      awaitItem().let {
        it.shouldBeTypeOf<FetchingSealedDelegatedDecryptionKeyStringData>()
        it.nfcTransaction.onSuccess(
          UnsealData.UnsealedDataResult("unsealed-data".encodeBase64().decodeBase64()!!)
        )
      }

      // Rotate spending keys
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()

      deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()

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

      awaitItem().shouldBeTypeOf<PerformingCloudBackupData>()
    }
  }

  test("ignore failure adding device token") {
    val recovery = recovery()
    deviceTokenManager.addDeviceTokenIfPresentForAccountReturn =
      DeviceTokenManagerResult.Err(NoDeviceToken)
    // Move clock ahead of delay period
    delay(delayDuration)
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Rotate auth keys
      accountAuthorizer.authResults = mutableListOf(accountAuthorizer.defaultErrorAuthResult)
      recoveryAuthCompleter.rotateAuthKeysResult = Ok(Unit)
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
        accountAuthorizer.authCalls.awaitItem()
        recoveryAuthCompleter.rotateAuthKeysCalls.awaitItem()
      }

      awaitItem().shouldBeTypeOf<FetchingSealedDelegatedDecryptionKeyFromF8eData>()

      awaitItem().let {
        it.shouldBeTypeOf<FetchingSealedDelegatedDecryptionKeyStringData>()
        it.nfcTransaction.onSuccess(
          UnsealData.UnsealedDataResult("unsealed-data".encodeBase64().decodeBase64()!!)
        )
      }

      // Rotate spending keys
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<RotatedSpendingKeys>()

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)
      relationshipsService.syncCalls.awaitItem()

      // Backing up new keybox
      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
        it.sealedCsek.shouldBe(SealedCsekFake)
        it.onBackupFinished()
      }

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
      deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()

      awaitItem().shouldBeTypeOf<PerformingSweepData>()
    }
  }

  test("descriptor backup processing - feature flag enabled - available data") {
    val recovery = CreatedSpendingKeys(
      fullAccountId = StillRecoveringInitiatedRecoveryMock.fullAccountId,
      appSpendingKey = StillRecoveringInitiatedRecoveryMock.appSpendingKey,
      appGlobalAuthKey = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKey,
      appRecoveryAuthKey = StillRecoveringInitiatedRecoveryMock.appRecoveryAuthKey,
      hardwareSpendingKey = StillRecoveringInitiatedRecoveryMock.hardwareSpendingKey,
      hardwareAuthKey = StillRecoveringInitiatedRecoveryMock.hardwareAuthKey,
      factorToRecover = App,
      appGlobalAuthKeyHwSignature = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKeyHwSignature,
      f8eSpendingKeyset = F8eSpendingKeysetMock,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake
    )

    descriptorBackupService.prepareDescriptorBackupsForRecoveryResult = Ok(
      DescriptorBackupPreparedData.Available(
        sealedSsek = SealedSsekFake,
        descriptorsToDecrypt = listOf(
          DescriptorBackup(
            keysetId = "test-keyset",
            sealedDescriptor = XCiphertext("test-descriptor")
          )
        ),
        keysetsToEncrypt = listOf(SpendingKeysetMock)
      )
    )

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().let {
        it.shouldBeTypeOf<HandlingDescriptorEncryption>()
        it.physicalFactor.shouldBe(App)
      }

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()
        .spendingKeysets.shouldBe(listOf(SpendingKeysetMock))

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)
      relationshipsService.syncCalls.awaitItem()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("descriptor backup processing - ssek unsealing success") {
    val recovery = CreatedSpendingKeys(
      fullAccountId = StillRecoveringInitiatedRecoveryMock.fullAccountId,
      appSpendingKey = StillRecoveringInitiatedRecoveryMock.appSpendingKey,
      appGlobalAuthKey = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKey,
      appRecoveryAuthKey = StillRecoveringInitiatedRecoveryMock.appRecoveryAuthKey,
      hardwareSpendingKey = StillRecoveringInitiatedRecoveryMock.hardwareSpendingKey,
      hardwareAuthKey = StillRecoveringInitiatedRecoveryMock.hardwareAuthKey,
      factorToRecover = App,
      appGlobalAuthKeyHwSignature = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKeyHwSignature,
      f8eSpendingKeyset = F8eSpendingKeysetMock,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake
    )

    descriptorBackupService.prepareDescriptorBackupsForRecoveryResult = Ok(
      DescriptorBackupPreparedData.NeedsUnsealed(
        sealedSsek = SealedSsekFake,
        descriptorsToDecrypt = listOf(
          DescriptorBackup(
            keysetId = "test-keyset",
            sealedDescriptor = XCiphertext("test-descriptor")
          )
        ),
        keysetsToEncrypt = listOf(SpendingKeysetMock)
      )
    )

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingSsekUnsealingData>()
        it.nfcTransaction.onSuccess(SsekFake)
      }

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()
        .spendingKeysets.shouldBe(listOf(SpendingKeysetMock))

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)
      relationshipsService.syncCalls.awaitItem()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("descriptor backup processing - ssek unsealing failure") {
    val recovery = CreatedSpendingKeys(
      fullAccountId = StillRecoveringInitiatedRecoveryMock.fullAccountId,
      appSpendingKey = StillRecoveringInitiatedRecoveryMock.appSpendingKey,
      appGlobalAuthKey = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKey,
      appRecoveryAuthKey = StillRecoveringInitiatedRecoveryMock.appRecoveryAuthKey,
      hardwareSpendingKey = StillRecoveringInitiatedRecoveryMock.hardwareSpendingKey,
      hardwareAuthKey = StillRecoveringInitiatedRecoveryMock.hardwareAuthKey,
      factorToRecover = App,
      appGlobalAuthKeyHwSignature = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKeyHwSignature,
      f8eSpendingKeyset = F8eSpendingKeysetMock,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake
    )

    descriptorBackupService.prepareDescriptorBackupsForRecoveryResult = Ok(
      DescriptorBackupPreparedData.NeedsUnsealed(
        sealedSsek = SealedSsekFake,
        descriptorsToDecrypt = listOf(
          DescriptorBackup(
            keysetId = "test-keyset",
            sealedDescriptor = XCiphertext("test-descriptor")
          )
        ),
        keysetsToEncrypt = listOf(SpendingKeysetMock)
      )
    )

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingSsekUnsealingData>()
        it.nfcTransaction.onCancel()
      }

      awaitItem().let {
        it.shouldBeTypeOf<FailedToProcessDescriptorBackupsData>()
        it.physicalFactor.shouldBe(App)
      }
    }
  }

  test("descriptor backup processing - feature flag disabled - skips descriptor backup flow") {
    val recovery = CreatedSpendingKeys(
      fullAccountId = StillRecoveringInitiatedRecoveryMock.fullAccountId,
      appSpendingKey = StillRecoveringInitiatedRecoveryMock.appSpendingKey,
      appGlobalAuthKey = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKey,
      appRecoveryAuthKey = StillRecoveringInitiatedRecoveryMock.appRecoveryAuthKey,
      hardwareSpendingKey = StillRecoveringInitiatedRecoveryMock.hardwareSpendingKey,
      hardwareAuthKey = StillRecoveringInitiatedRecoveryMock.hardwareAuthKey,
      factorToRecover = App,
      appGlobalAuthKeyHwSignature = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKeyHwSignature,
      f8eSpendingKeyset = F8eSpendingKeysetMock,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake
    )

    // Explicitly disable feature flag for this test
    encryptedDescriptorBackupsFeatureFlag.setFlagValue(BooleanFlag(false))

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<PerformingDdkBackupData>()
        it.physicalFactor.shouldBe(App)
      }

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)
      relationshipsService.syncCalls.awaitItem()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("descriptor backup processing - feature flag disabled during recovery rollback") {
    val recovery = UploadedDescriptorBackups(
      fullAccountId = StillRecoveringInitiatedRecoveryMock.fullAccountId,
      appSpendingKey = StillRecoveringInitiatedRecoveryMock.appSpendingKey,
      appGlobalAuthKey = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKey,
      appRecoveryAuthKey = StillRecoveringInitiatedRecoveryMock.appRecoveryAuthKey,
      hardwareSpendingKey = StillRecoveringInitiatedRecoveryMock.hardwareSpendingKey,
      hardwareAuthKey = StillRecoveringInitiatedRecoveryMock.hardwareAuthKey,
      factorToRecover = App,
      appGlobalAuthKeyHwSignature = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKeyHwSignature,
      f8eSpendingKeyset = F8eSpendingKeysetMock,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake,
      keysets = listOf(SpendingKeysetMock)
    )

    // Explicitly disable feature flag to simulate it being disabled after descriptor backups were uploaded
    encryptedDescriptorBackupsFeatureFlag.setFlagValue(BooleanFlag(false))

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<PerformingDdkBackupData>()
        it.physicalFactor.shouldBe(App)
      }

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)
      relationshipsService.syncCalls.awaitItem()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("descriptor backup processing - preparation failure") {
    val recovery = CreatedSpendingKeys(
      fullAccountId = StillRecoveringInitiatedRecoveryMock.fullAccountId,
      appSpendingKey = StillRecoveringInitiatedRecoveryMock.appSpendingKey,
      appGlobalAuthKey = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKey,
      appRecoveryAuthKey = StillRecoveringInitiatedRecoveryMock.appRecoveryAuthKey,
      hardwareSpendingKey = StillRecoveringInitiatedRecoveryMock.hardwareSpendingKey,
      hardwareAuthKey = StillRecoveringInitiatedRecoveryMock.hardwareAuthKey,
      factorToRecover = App,
      appGlobalAuthKeyHwSignature = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKeyHwSignature,
      f8eSpendingKeyset = F8eSpendingKeysetMock,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake
    )

    descriptorBackupService.prepareDescriptorBackupsForRecoveryResult =
      Err(Error("Preparation failed"))

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().let {
        it.shouldBeTypeOf<FailedToProcessDescriptorBackupsData>()
        it.physicalFactor.shouldBe(App)
        it.onRetry()
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<FailedToProcessDescriptorBackupsData>()
    }
  }

  test("descriptor backup processing - upload failure") {
    val recovery = CreatedSpendingKeys(
      fullAccountId = StillRecoveringInitiatedRecoveryMock.fullAccountId,
      appSpendingKey = StillRecoveringInitiatedRecoveryMock.appSpendingKey,
      appGlobalAuthKey = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKey,
      appRecoveryAuthKey = StillRecoveringInitiatedRecoveryMock.appRecoveryAuthKey,
      hardwareSpendingKey = StillRecoveringInitiatedRecoveryMock.hardwareSpendingKey,
      hardwareAuthKey = StillRecoveringInitiatedRecoveryMock.hardwareAuthKey,
      factorToRecover = App,
      appGlobalAuthKeyHwSignature = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKeyHwSignature,
      f8eSpendingKeyset = F8eSpendingKeysetMock,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake
    )

    descriptorBackupService.prepareDescriptorBackupsForRecoveryResult = Ok(
      DescriptorBackupPreparedData.Available(
        sealedSsek = SealedSsekFake,
        descriptorsToDecrypt = listOf(
          DescriptorBackup(
            keysetId = "test-keyset",
            sealedDescriptor = XCiphertext("test-descriptor")
          )
        ),
        keysetsToEncrypt = listOf(SpendingKeysetMock)
      )
    )
    descriptorBackupService.uploadDescriptorBackupsResult =
      Err(DescriptorBackupError.NetworkError(RuntimeException("Network error")))

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()

      awaitItem().let {
        it.shouldBeTypeOf<FailedToProcessDescriptorBackupsData>()
        it.physicalFactor.shouldBe(App)
      }
    }
  }

  test("descriptor backup processing - encrypt only scenario") {
    val recovery = CreatedSpendingKeys(
      fullAccountId = StillRecoveringInitiatedRecoveryMock.fullAccountId,
      appSpendingKey = StillRecoveringInitiatedRecoveryMock.appSpendingKey,
      appGlobalAuthKey = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKey,
      appRecoveryAuthKey = StillRecoveringInitiatedRecoveryMock.appRecoveryAuthKey,
      hardwareSpendingKey = StillRecoveringInitiatedRecoveryMock.hardwareSpendingKey,
      hardwareAuthKey = StillRecoveringInitiatedRecoveryMock.hardwareAuthKey,
      factorToRecover = App,
      appGlobalAuthKeyHwSignature = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKeyHwSignature,
      f8eSpendingKeyset = F8eSpendingKeysetMock,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake
    )

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingHardwareProofOfPossessionData>()
        it.addHwFactorProofOfPossession(HwFactorProofOfPossession("signed-token"))
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)
      relationshipsService.syncCalls.awaitItem()

      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
        it.onBackupFinished()
      }

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("initial state calculation - CreatedSpendingKeys with missing sealedSsek skips descriptor backup flow") {
    val recovery = CreatedSpendingKeys(
      fullAccountId = StillRecoveringInitiatedRecoveryMock.fullAccountId,
      appSpendingKey = StillRecoveringInitiatedRecoveryMock.appSpendingKey,
      appGlobalAuthKey = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKey,
      appRecoveryAuthKey = StillRecoveringInitiatedRecoveryMock.appRecoveryAuthKey,
      hardwareSpendingKey = StillRecoveringInitiatedRecoveryMock.hardwareSpendingKey,
      hardwareAuthKey = StillRecoveringInitiatedRecoveryMock.hardwareAuthKey,
      factorToRecover = App,
      appGlobalAuthKeyHwSignature = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKeyHwSignature,
      f8eSpendingKeyset = F8eSpendingKeysetMock,
      sealedCsek = SealedCsekFake,
      sealedSsek = null // Missing sealedSsek should skip descriptor backup flow
    )

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<PerformingDdkBackupData>()
        it.physicalFactor.shouldBe(App)
        it.keybox.keysets.size.shouldBe(1)
      }

      // Verify that recovery progress is set for DDK backup
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)
      relationshipsService.syncCalls.awaitItem()

      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
        it.onBackupFinished()
      }

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()

      awaitItem().shouldBeTypeOf<PerformingSweepData>()
    }
  }

  test("initial state calculation - UploadedDescriptorBackups uses recovery keysets") {
    val recovery = UploadedDescriptorBackups(
      fullAccountId = StillRecoveringInitiatedRecoveryMock.fullAccountId,
      appSpendingKey = StillRecoveringInitiatedRecoveryMock.appSpendingKey,
      appGlobalAuthKey = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKey,
      appRecoveryAuthKey = StillRecoveringInitiatedRecoveryMock.appRecoveryAuthKey,
      hardwareSpendingKey = StillRecoveringInitiatedRecoveryMock.hardwareSpendingKey,
      hardwareAuthKey = StillRecoveringInitiatedRecoveryMock.hardwareAuthKey,
      factorToRecover = App,
      appGlobalAuthKeyHwSignature = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKeyHwSignature,
      f8eSpendingKeyset = F8eSpendingKeysetMock,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake,
      keysets = listOf(SpendingKeysetMock, SpendingKeysetMock) // Multiple keysets to verify they're used
    )

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<PerformingDdkBackupData>()
        it.physicalFactor.shouldBe(App)
        // Verify that the recovery keysets are used when feature flag is enabled
        it.keybox.keysets.size.shouldBe(2)
        it.keybox.keysets.shouldBe(recovery.keysets)
      }

      // Verify that recovery progress is set for DDK backup
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()
      relationshipsService.syncCalls.awaitItem()

      cancelAndIgnoreRemainingEvents()
    }
  }
})
