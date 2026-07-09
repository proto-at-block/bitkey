@file:OptIn(DelicateCoroutinesApi::class)

package build.wallet.statemachine.data.recovery.inprogress

import app.cash.turbine.ReceiveTurbine
import bitkey.account.AccountConfigServiceFake
import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope
import bitkey.auth.RefreshToken
import bitkey.backup.DescriptorBackup
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.SpecificClientErrorMock
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import bitkey.recovery.DelayNotifyServiceFake
import bitkey.recovery.DescriptorBackupError
import bitkey.recovery.DescriptorBackupPreparedData
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.auth.AppGlobalAuthPublicKeyMock2
import build.wallet.bitkey.challange.DelayNotifyRecoveryChallengeFake
import build.wallet.bitkey.challange.SignedChallenge
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.F8eSpendingKeysetPrivateWalletMock
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.csek.*
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.recovery.SignedKeysetVerificationResponseMock
import build.wallet.f8e.relationships.RelationshipsFake
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareMetadata
import build.wallet.firmware.SecureBootConfig
import build.wallet.fwup.FirmwareData
import build.wallet.fwup.FirmwareDataServiceFake
import build.wallet.ktor.result.HttpError
import build.wallet.nfc.NfcSessionFake
import build.wallet.nfc.W3NfcCommandsMock
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.RecoveryAuthorizeLostHwResult
import build.wallet.nfc.transaction.NfcTransaction
import build.wallet.nfc.transaction.ProvisionAppAuthKeyTransactionProviderFake
import build.wallet.nfc.transaction.RecoveryNfcSession
import build.wallet.nfc.transaction.RecoveryProofAndKeyTransferLostApp.ProofAndKeyTransferLostAppResult
import build.wallet.nfc.transaction.RecoveryProofAndKeyTransferLostHw.ProofAndKeyTransferLostHwResult
import build.wallet.nfc.transaction.SignChallengeAndSealSeks.SignedChallengeAndSeks
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.recovery.*
import build.wallet.recovery.CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError
import build.wallet.recovery.Recovery
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.recovery.Recovery.StillRecovering.ServerIndependentRecovery.*
import build.wallet.relationships.*
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.*
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
import io.ktor.util.encodeBase64
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// Large end-to-end coverage for recovery-in-progress flows; splitting would hurt cohesion.
@Suppress("LargeClass")
class RecoveryInProgressDataStateMachineImplTests : FunSpec({
  val clock = ClockFake()
  val delayNotifyService = DelayNotifyServiceFake()
  val sekGenerator = SekGeneratorMock()
  val csekDao = CsekDaoFake()
  val ssekDao = SsekDaoFake()
  val uuid = UuidGeneratorFake()
  val recoveryStatusService =
    RecoveryStatusServiceMock(StillRecoveringInitiatedRecoveryMock, turbines::create)
  val relationshipsService = RelationshipsServiceMock(turbines::create, clock)
  val accountConfigService = AccountConfigServiceFake()
  val descriptorBackupService = DescriptorBackupServiceFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val fingerprintResetMinFirmwareVersionFeatureFlag = FingerprintResetMinFirmwareVersionFeatureFlag(
    featureFlagDao = FeatureFlagDaoFake()
  )
  val nfcCommandsMock = W3NfcCommandsMock(turbines::create)
  val firmwareDataService = FirmwareDataServiceFake()
  val fakeChallenge = SignedChallenge.HardwareSignedChallenge(
    challenge = DelayNotifyRecoveryChallengeFake,
    signature = ""
  )
  val delegatedDecryptionKeyService = DelegatedDecryptionKeyServiceMock(
    uploadCalls = turbines.create("upload calls")
  )
  val authTokensService = AuthTokensServiceFake()
  val actionProofService = bitkey.privilegedactions.ActionProofServiceFake()

  // Restore relationshipsKeysRepository so it is still passed and used
  val relationshipsKeysRepository = RelationshipsKeysRepository(
    relationshipsCrypto = RelationshipsCryptoFake(),
    relationshipKeysDao = RelationshipsKeysDaoFake()
  )

  val stateMachine = RecoveryInProgressDataStateMachineImpl(
    actionProofService = actionProofService,
    delayNotifyService = delayNotifyService,
    clock = Clock.System,
    sekGenerator = sekGenerator,
    csekDao = csekDao,
    ssekDao = ssekDao,
    uuidGenerator = uuid,
    recoveryStatusService = recoveryStatusService,
    relationshipsService = relationshipsService,
    delegatedDecryptionKeyService = delegatedDecryptionKeyService,
    relationshipsKeysRepository = relationshipsKeysRepository,
    minimumLoadingDuration = MinimumLoadingDuration(0.seconds),
    accountConfigService = accountConfigService,
    descriptorBackupService = descriptorBackupService,
    provisionAppAuthKeyTransactionProvider = ProvisionAppAuthKeyTransactionProviderFake(),
    firmwareDataService = firmwareDataService,
    minFirmwareVersionFeatureFlag = fingerprintResetMinFirmwareVersionFeatureFlag,
    authTokensService = authTokensService
  )

  beforeTest {
    csekDao.reset()
    ssekDao.reset()
    nfcCommandsMock.reset()
    relationshipsService.relationshipsFlow.emit(RelationshipsFake)
    accountConfigService.reset()
    delayNotifyService.reset()
    descriptorBackupService.reset()
    featureFlagDao.reset()
    fingerprintResetMinFirmwareVersionFeatureFlag.reset()
    firmwareDataService.reset()
    authTokensService.reset()
    actionProofService.reset()
    delegatedDecryptionKeyService.reset()
    // Set up tokens so PreparingProofAndKeyTransferState can refresh
    authTokensService.setTokens(
      accountId = StillRecoveringInitiatedRecoveryMock.fullAccountId,
      tokens = AccountAuthTokens(
        accessToken = AccessToken("fake-access-token"),
        refreshToken = RefreshToken("fake-refresh-token"),
        accessTokenExpiresAt = null,
        refreshTokenExpiresAt = null
      ),
      scope = AuthTokenScope.Global
    )
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

  fun props(recovery: StillRecovering = recovery()) =
    RecoveryInProgressProps(
      recovery = recovery,
      oldAppGlobalAuthKey = null
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

      awaitUntil { it is AwaitingProofOfPossessionForCancellationData }.let {
        it.shouldBeTypeOf<AwaitingProofOfPossessionForCancellationData>()
        it.addProof(PrivilegedActionProof.HwKeyProof(HwFactorProofOfPossession("")))
      }

      awaitItem().shouldBeTypeOf<CancellingData>()
    }
  }

  test("cancel lost hw recovery while delay period is pending") {
    val recovery = recovery().copy(factorToRecover = Hardware)
    stateMachine.test(
      props(recovery)
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
    delayNotifyService.cancelResult =
      Err(
        F8eCancelDelayNotifyError(F8eError.UnhandledException(HttpError.UnhandledException(Throwable())))
      )

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.cancel()
      }

      awaitUntil { it is AwaitingProofOfPossessionForCancellationData }.let {
        it.shouldBeTypeOf<AwaitingProofOfPossessionForCancellationData>()
        it.addProof(PrivilegedActionProof.HwKeyProof(HwFactorProofOfPossession("")))
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
    delayNotifyService.cancelResult =
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

      awaitUntil { it is AwaitingProofOfPossessionForCancellationData }.let {
        it.shouldBeTypeOf<AwaitingProofOfPossessionForCancellationData>()
        it.addProof(PrivilegedActionProof.HwKeyProof(HwFactorProofOfPossession("")))
      }

      awaitItem().shouldBeTypeOf<CancellingData>()

      with(awaitItem()) {
        shouldBeTypeOf<VerifyingNotificationCommsForCancellationData>()
        delayNotifyService.reset()
        onComplete()
      }

      awaitUntil { it is AwaitingProofOfPossessionForCancellationData }.let {
        it.shouldBeTypeOf<AwaitingProofOfPossessionForCancellationData>()
        it.addProof(PrivilegedActionProof.HwKeyProof(HwFactorProofOfPossession("")))
      }

      awaitItem().shouldBeTypeOf<CancellingData>()
    }
  }

  test("cancel lost app recovery with CommsVerificationRequiredError routes to comms verification") {
    val recovery = recovery()
    // Move clock ahead of delay period end time
    delay(delayDuration)
    delayNotifyService.cancelResult =
      Err(
        CancelDelayNotifyRecoveryError.CommsVerificationRequiredError(Error("comms required"))
      )
    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.cancel()
      }

      awaitUntil { it is AwaitingProofOfPossessionForCancellationData }.let {
        it.shouldBeTypeOf<AwaitingProofOfPossessionForCancellationData>()
        it.addProof(PrivilegedActionProof.HwKeyProof(HwFactorProofOfPossession("")))
      }

      awaitItem().shouldBeTypeOf<CancellingData>()

      with(awaitItem()) {
        shouldBeTypeOf<VerifyingNotificationCommsForCancellationData>()
        delayNotifyService.reset()
        onComplete()
      }

      awaitUntil { it is AwaitingProofOfPossessionForCancellationData }.let {
        it.shouldBeTypeOf<AwaitingProofOfPossessionForCancellationData>()
        it.addProof(PrivilegedActionProof.HwKeyProof(HwFactorProofOfPossession("")))
      }

      awaitItem().shouldBeTypeOf<CancellingData>()
    }
  }

  test("cancel lost hw recovery when it is ready to be completed and requires notification comms") {
    val recovery = recovery().copy(factorToRecover = Hardware)
    // Move clock ahead of delay period end time
    delay(delayDuration)
    delayNotifyService.cancelResult =
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
        delayNotifyService.reset()
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
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        csekDao.setResult = Err(IllegalStateException())
        it.nfcTransaction.onSuccess(
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            csek = CsekFake,
            ssek = SsekFake,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().shouldBeTypeOf<FailedToRotateAuthData>()
    }
  }

  test("invalid sealed SSEK from hardware tap 1 fails before persisting") {
    val recovery = recovery()
    val invalidSealedSsek = "0a01ff".decodeHex()
    delay(delayDuration)

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            csek = CsekFake,
            ssek = SsekFake,
            sealedCsek = SealedCsekFake,
            sealedSsek = invalidSealedSsek
          )
        )
      }

      awaitItem().shouldBeTypeOf<FailedToRotateAuthData>().cause.message shouldBe
        "Invalid sealed SSEK from hardware tap 1: data length must be 32 bytes"
    }
  }

  test("complete recovery with socrec - descriptor backups enabled") {
    val recovery = recovery()

    // Move clock ahead of delay period
    delay(delayDuration)

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Rotate auth keys
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            csek = CsekFake,
            ssek = SsekFake,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
      }

      awaitItem().shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofAndKeyTransferLostAppData>()
        it.nfcTransaction.onSuccess(
          ProofAndKeyTransferLostAppResult(
            hwProofOfPossession = HwFactorProofOfPossession("signed-token"),
            unsealedDdkData = "unsealed-data".encodeBase64().decodeBase64(),
            unsealedOldSsek = null,
            ddkUnsealFailed = false
          )
        )
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()
        .spendingKeysets.shouldBe(listOf(SpendingKeysetMock))

      // Activation → provisioning tap → DDK backup
      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()
      awaitItem().let {
        it.shouldBeTypeOf<ProvisioningAppAuthKeyToHardwareData>()
        it.nfcTransaction.onSuccess(Unit)
      }

      // Persisting hw descriptor validation progress
      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.HwDescriptorValidated>()

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)

      // Backing up new keybox
      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
        it.sealedCsek.shouldBe(SealedCsekFake)
        it.onBackupFinished()
      }

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.BackedUpToCloud>()

      // Sweeping funds
      awaitItem().shouldBeTypeOf<PerformingSweepData>()
    }
  }

  test("DDK unseal failure during tap 2 shows DDK error screen") {
    val recovery = recovery()
    delay(delayDuration)

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            csek = CsekFake,
            ssek = SsekFake,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().shouldBeTypeOf<RotatingAuthKeysWithF8eData>()

      // After token rotation, creates spending keys then prepares for tap 2
      awaitItem().shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      // Tap 2 succeeds but DDK unseal failed inside the session
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofAndKeyTransferLostAppData>()
        it.nfcTransaction.onSuccess(
          ProofAndKeyTransferLostAppResult(
            hwProofOfPossession = HwFactorProofOfPossession("signed-token"),
            unsealedDdkData = null,
            unsealedOldSsek = null,
            ddkUnsealFailed = true
          )
        )
      }

      // Should show DDK error screen with retry/remove options
      awaitItem().shouldBeTypeOf<DelegatedDecryptionKeyErrorStateData>()

      cancelAndIgnoreRemainingEvents()
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
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            csek = CsekFake,
            ssek = SsekFake,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
      }

      awaitItem().shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofAndKeyTransferLostAppData>()
        it.nfcTransaction.onSuccess(
          ProofAndKeyTransferLostAppResult(
            hwProofOfPossession = HwFactorProofOfPossession("signed-token"),
            unsealedDdkData = "unsealed-data".encodeBase64().decodeBase64(),
            unsealedOldSsek = null,
            ddkUnsealFailed = false
          )
        )
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()
        .spendingKeysets.shouldBe(listOf(SpendingKeysetMock))

      // Activation → provisioning tap → DDK backup
      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()
      awaitItem().let {
        it.shouldBeTypeOf<ProvisioningAppAuthKeyToHardwareData>()
        it.nfcTransaction.onSuccess(Unit)
      }

      // Persisting hw descriptor validation progress
      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.HwDescriptorValidated>()

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)

      // Backing up new keybox
      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
        it.sealedCsek.shouldBe(SealedCsekFake)
        it.onBackupFinished()
      }

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.BackedUpToCloud>()

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
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            csek = CsekFake,
            ssek = SsekFake,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
      }

      awaitItem().shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofAndKeyTransferLostHwData>()
        it.nfcTransaction.onSuccess(
          ProofAndKeyTransferLostHwResult(
            hwProofOfPossession = HwFactorProofOfPossession("signed-token"),
            sealedDdkData = "FakeSealedData".encodeBase64().decodeBase64()
          )
        )
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()
        .spendingKeysets.shouldBe(listOf(SpendingKeysetMock))

      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()

      // CheckingProvisioningAfterActivation → provisioning tap → persist HwDescriptorValidated → DDK backup
      awaitUntil { it is ProvisioningAppAuthKeyToHardwareData }.let {
        it.shouldBeTypeOf<ProvisioningAppAuthKeyToHardwareData>()
        it.nfcTransaction.onSuccess(Unit)
      }

      // Persisting hw descriptor validation progress
      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.HwDescriptorValidated>()

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()
      delegatedDecryptionKeyService.uploadCalls!!.awaitItem()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)

      // Backing up new keybox
      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
        it.sealedCsek.shouldBe(SealedCsekFake)
        it.onBackupFinished()
      }

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()

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
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            csek = CsekFake,
            ssek = SsekFake,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
      }

      awaitItem().shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofAndKeyTransferLostAppData>()
        it.nfcTransaction.onSuccess(
          ProofAndKeyTransferLostAppResult(
            hwProofOfPossession = HwFactorProofOfPossession("signed-token"),
            unsealedDdkData = "unsealed-data".encodeBase64().decodeBase64(),
            unsealedOldSsek = null,
            ddkUnsealFailed = false
          )
        )
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()

      // Activation → provisioning tap → DDK backup
      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()
      awaitItem().let {
        it.shouldBeTypeOf<ProvisioningAppAuthKeyToHardwareData>()
        it.nfcTransaction.onSuccess(Unit)
      }

      // Persisting hw descriptor validation progress
      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.HwDescriptorValidated>()

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)

      // Backing up new keybox
      awaitItem().let {
        it.shouldBeTypeOf<PerformingCloudBackupData>()
        it.sealedCsek.shouldBe(SealedCsekFake)
        it.onBackupFinished()
      }

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()

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
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            csek = CsekFake,
            ssek = SsekFake,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
      }

      awaitItem().shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofAndKeyTransferLostAppData>()
        it.nfcTransaction.onSuccess(
          ProofAndKeyTransferLostAppResult(
            hwProofOfPossession = HwFactorProofOfPossession("signed-token"),
            unsealedDdkData = "unsealed-data".encodeBase64().decodeBase64(),
            unsealedOldSsek = null,
            ddkUnsealFailed = false
          )
        )
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()

      // Activation → provisioning tap → DDK backup
      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()
      awaitItem().let {
        it.shouldBeTypeOf<ProvisioningAppAuthKeyToHardwareData>()
        it.nfcTransaction.onSuccess(Unit)
      }

      // Persisting hw descriptor validation progress
      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.HwDescriptorValidated>()

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)

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
      sealedSsek = SealedSsekFake,
      originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2
    )

    descriptorBackupService.prepareDescriptorBackupsForRecoveryResult = Ok(
      DescriptorBackupPreparedData.Available(
        sealedSsek = SealedSsekFake,
        descriptorsToDecrypt = listOf(
          DescriptorBackup(
            keysetId = "test-keyset",
            sealedDescriptor = XCiphertext("test-descriptor"),
            privateWalletRootXpub = XCiphertext("test-private-wallet-root-xpub")
          )
        ),
        keysetsToEncrypt = listOf(SpendingKeysetMock)
      )
    )

    stateMachine.test(props(recovery)) {
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      // Drive through tap 2 to reach descriptor backup processing
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofAndKeyTransferLostAppData>()
        it.nfcTransaction.onSuccess(
          ProofAndKeyTransferLostAppResult(
            hwProofOfPossession = HwFactorProofOfPossession("signed-token"),
            unsealedDdkData = null,
            unsealedOldSsek = null,
            ddkUnsealFailed = false
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<HandlingDescriptorEncryption>()
        it.physicalFactor.shouldBe(App)
      }

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()
        .spendingKeysets.shouldBe(listOf(SpendingKeysetMock))

      // Activation → provisioning tap → DDK backup
      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()
      awaitItem().let {
        it.shouldBeTypeOf<ProvisioningAppAuthKeyToHardwareData>()
        it.nfcTransaction.onSuccess(Unit)
      }

      // Persisting hw descriptor validation progress
      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.HwDescriptorValidated>()

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)

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
      sealedSsek = SealedSsekFake,
      originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2
    )

    descriptorBackupService.prepareDescriptorBackupsForRecoveryResult =
      Err(Error("Preparation failed"))

    stateMachine.test(props(recovery)) {
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      // Drive through tap 2 to reach descriptor backup processing
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofAndKeyTransferLostAppData>()
        it.nfcTransaction.onSuccess(
          ProofAndKeyTransferLostAppResult(
            hwProofOfPossession = HwFactorProofOfPossession("signed-token"),
            unsealedDdkData = null,
            unsealedOldSsek = null,
            ddkUnsealFailed = false
          )
        )
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
      sealedSsek = SealedSsekFake,
      originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2
    )

    descriptorBackupService.prepareDescriptorBackupsForRecoveryResult = Ok(
      DescriptorBackupPreparedData.Available(
        sealedSsek = SealedSsekFake,
        descriptorsToDecrypt = listOf(
          DescriptorBackup(
            keysetId = "test-keyset",
            sealedDescriptor = XCiphertext("test-descriptor"),
            privateWalletRootXpub = XCiphertext("test-private-wallet-root-xpub")
          )
        ),
        keysetsToEncrypt = listOf(SpendingKeysetMock)
      )
    )
    descriptorBackupService.uploadDescriptorBackupsResult =
      Err(DescriptorBackupError.NetworkError(RuntimeException("Network error")))

    stateMachine.test(props(recovery)) {
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      // Drive through tap 2 to reach descriptor backup processing
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofAndKeyTransferLostAppData>()
        it.nfcTransaction.onSuccess(
          ProofAndKeyTransferLostAppResult(
            hwProofOfPossession = HwFactorProofOfPossession("signed-token"),
            unsealedDdkData = null,
            unsealedOldSsek = null,
            ddkUnsealFailed = false
          )
        )
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
      sealedSsek = SealedSsekFake,
      originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2
    )

    stateMachine.test(props(recovery)) {
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      // Drive through tap 2 to reach descriptor backup processing
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofAndKeyTransferLostAppData>()
        it.nfcTransaction.onSuccess(
          ProofAndKeyTransferLostAppResult(
            hwProofOfPossession = HwFactorProofOfPossession("signed-token"),
            unsealedDdkData = null,
            unsealedOldSsek = null,
            ddkUnsealFailed = false
          )
        )
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()

      // Activation → provisioning tap → DDK backup
      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()
      awaitItem().let {
        it.shouldBeTypeOf<ProvisioningAppAuthKeyToHardwareData>()
        it.nfcTransaction.onSuccess(Unit)
      }

      // Persisting hw descriptor validation progress
      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.HwDescriptorValidated>()

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      awaitItem().shouldBe(RegeneratingTcCertificatesData)

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
      sealedSsek = null, // Missing sealedSsek should skip descriptor backup flow
      originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2
    )

    stateMachine.test(props(recovery)) {
      // CreatedSpendingKeys now goes to PreparingProofAndKeyTransferData (two-tap flow)
      awaitItem().let {
        it.shouldBeTypeOf<PreparingProofAndKeyTransferData>()
        it.physicalFactor.shouldBe(App)
      }

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("initial state calculation - ActivatedSpendingKeys starts with uploading the ddk") {
    val recovery = ActivatedSpendingKeys(
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
      keysets = listOf(SpendingKeysetMock, SpendingKeysetMock),
      originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2
    )

    stateMachine.test(props(recovery)) {
      // ActivatedSpendingKeys → provisioning tap → persist HwDescriptorValidated → DDK backup
      awaitItem().let {
        it.shouldBeTypeOf<ProvisioningAppAuthKeyToHardwareData>()
        it.nfcTransaction.onSuccess(Unit)
      }

      // Persisting hw descriptor validation progress (shows as ActivatingSpendingKeysetData)
      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.HwDescriptorValidated>()

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("ActivatedSpendingKeys skips provisioning when firmware below threshold") {
    val recovery = ActivatedSpendingKeys(
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
      keysets = listOf(SpendingKeysetMock),
      originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2
    )

    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.0.98"))
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = createFirmwareDeviceInfo("0.0.1"),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    stateMachine.test(props(recovery)) {
      // Firmware below threshold → skips provisioning, straight to DDK backup
      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("ActivatedSpendingKeys with supported firmware enters provisioning") {
    val recovery = ActivatedSpendingKeys(
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
      keysets = listOf(SpendingKeysetMock),
      originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2
    )

    // Firmware meets threshold
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.0.98"))
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = createFirmwareDeviceInfo("1.0.99"),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    stateMachine.test(props(recovery)) {
      // Firmware meets threshold → provisioning tap → persist HwDescriptorValidated → DDK
      awaitItem().let {
        it.shouldBeTypeOf<ProvisioningAppAuthKeyToHardwareData>()
        it.nfcTransaction.onSuccess(Unit)
      }

      // Persisting hw descriptor validation progress
      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.HwDescriptorValidated>()

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("post-activation provisioning cancel goes to failed state then retries") {
    val recovery = ActivatedSpendingKeys(
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
      keysets = listOf(SpendingKeysetMock),
      originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2
    )

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ProvisioningAppAuthKeyToHardwareData>()
        // Cancel — goes to FailedToBuildHardwareDescriptorState
        it.nfcTransaction.onCancel()
      }

      // Cancel goes to FailedToBuildHardwareDescriptorData, retry goes back to PoP
      awaitItem().let {
        it.shouldBeTypeOf<FailedToBuildHardwareDescriptorData>()
        it.onRetry()
      }

      // Retry goes back to PreparingProofAndKeyTransferData (two-tap flow)
      awaitItem().let {
        it.shouldBeTypeOf<PreparingProofAndKeyTransferData>()
      }

      cancelAndIgnoreRemainingEvents()
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
      keysets = listOf(SpendingKeysetMock, SpendingKeysetMock), // Multiple keysets to verify they're used
      originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2
    )

    stateMachine.test(props(recovery)) {
      // UploadedDescriptorBackups now goes through PreparingProofAndKeyTransferData (two-tap flow)
      awaitItem().let {
        it.shouldBeTypeOf<PreparingProofAndKeyTransferData>()
        it.physicalFactor.shouldBe(App)
      }

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("initial state calculation - SweepAttempted enters performing sweep") {
    val recovery = SweepAttempted(
      fullAccountId = StillRecoveringInitiatedRecoveryMock.fullAccountId,
      appSpendingKey = StillRecoveringInitiatedRecoveryMock.appSpendingKey,
      appGlobalAuthKey = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKey,
      appRecoveryAuthKey = StillRecoveringInitiatedRecoveryMock.appRecoveryAuthKey,
      hardwareSpendingKey = StillRecoveringInitiatedRecoveryMock.hardwareSpendingKey,
      hardwareAuthKey = StillRecoveringInitiatedRecoveryMock.hardwareAuthKey,
      factorToRecover = App,
      appGlobalAuthKeyHwSignature = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKeyHwSignature,
      f8eSpendingKeyset = F8eSpendingKeysetMock,
      keysets = listOf(SpendingKeysetMock, SpendingKeysetMock),
      originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2
    )

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<PerformingSweepData>()
        it.keybox.keysets.shouldBe(recovery.keysets)
      }
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("initial state calculation - hardware recovery sweep uses recovered hardware type from firmware") {
    accountConfigService.setActiveConfig(
      FullAccountConfig(
        bitcoinNetworkType = BitcoinNetworkType.SIGNET,
        f8eEnvironment = build.wallet.f8e.F8eEnvironment.Development,
        isTestAccount = true,
        isUsingSocRecFakes = true,
        isHardwareFake = true,
        hardwareType = HardwareType.W3
      )
    )
    firmwareDataService.firmwareData.value = firmwareDataService.firmwareData.value.copy(
      firmwareDeviceInfo = createFirmwareDeviceInfo("1.0.0")
    )

    val recovery = SweepAttempted(
      fullAccountId = StillRecoveringInitiatedRecoveryMock.fullAccountId,
      appSpendingKey = StillRecoveringInitiatedRecoveryMock.appSpendingKey,
      appGlobalAuthKey = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKey,
      appRecoveryAuthKey = StillRecoveringInitiatedRecoveryMock.appRecoveryAuthKey,
      hardwareSpendingKey = StillRecoveringInitiatedRecoveryMock.hardwareSpendingKey,
      hardwareAuthKey = StillRecoveringInitiatedRecoveryMock.hardwareAuthKey,
      factorToRecover = Hardware,
      appGlobalAuthKeyHwSignature = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKeyHwSignature,
      f8eSpendingKeyset = F8eSpendingKeysetMock,
      keysets = listOf(SpendingKeysetMock),
      originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2
    )

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<PerformingSweepData>()
        it.keybox.config.hardwareType.shouldBe(HardwareType.W1)
      }
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("firmware version check - all token rotation paths go through preparing proof and key transfer") {
    val recovery = recovery()
    delay(delayDuration)

    // Regardless of firmware version, after token rotation we now go to PreparingProofAndKeyTransfer
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.0.98"))
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = createFirmwareDeviceInfo("1.0.98"),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    stateMachine.test(props(recovery)) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            csek = CsekFake,
            ssek = SsekFake,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().shouldBeTypeOf<RotatingAuthKeysWithF8eData>()

      // After token rotation, creates spending keys then prepares for tap 2
      awaitItem().shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("RotatedAuthKeys resume always goes to PreparingProofAndKeyTransferData") {
    val recovery = RotatedAuthKeys(
      fullAccountId = StillRecoveringInitiatedRecoveryMock.fullAccountId,
      appSpendingKey = StillRecoveringInitiatedRecoveryMock.appSpendingKey,
      appGlobalAuthKey = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKey,
      appRecoveryAuthKey = StillRecoveringInitiatedRecoveryMock.appRecoveryAuthKey,
      hardwareSpendingKey = StillRecoveringInitiatedRecoveryMock.hardwareSpendingKey,
      hardwareSpendingKeyProof = null,
      hardwareAuthKey = StillRecoveringInitiatedRecoveryMock.hardwareAuthKey,
      factorToRecover = App,
      appGlobalAuthKeyHwSignature = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKeyHwSignature,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake,
      originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2
    )

    stateMachine.test(props(recovery)) {
      // RotatedAuthKeys now starts with creating spending keys, then preparing for tap 2
      awaitItem().shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("W3 hardware path exercises hardware descriptor validation") {
    val recovery = hardwareRecovery()
    val w3DeviceInfo = createFirmwareDeviceInfo("2.0.0", hwRevision = "w3a-core-evt")
    delay(delayDuration)

    // Set up W3 hardware: firmware reports W3 hwRevision + private wallet keyset
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = w3DeviceInfo,
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )
    nfcCommandsMock.deviceInfoResult = w3DeviceInfo
    descriptorBackupService.getNextAccountIndexResult = Ok(1u)
    delayNotifyService.createSpendingKeysetResult = com.github.michaelbull.result.Ok(F8eSpendingKeysetPrivateWalletMock)
    // activateSpendingKeyset returns signed keys for W3
    delayNotifyService.activateSpendingKeysetResult = com.github.michaelbull.result.Ok(SignedKeysetVerificationResponseMock)

    stateMachine.test(
      props = props(recovery),
      turbineTimeout = 10.seconds
    ) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Rotate auth keys (W3 confirmable)
      awaitW3Tap1Data().let {
        it.nfcSession.confirmableRunSessionAndOnSuccess(nfcCommandsMock)
        nfcCommandsMock.signChallengeAndSealSeksCalls.awaitItem()
      }

      awaitItem().shouldBeTypeOf<RotatingAuthKeysWithF8eData>()

      awaitItem().shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofAndKeyTransferLostHwData>()
        it.nfcSession.confirmableOnSuccess(
          RecoveryAuthorizeLostHwResult(
            descriptorBackupsSignature = "fake-descriptor-backups-sig",
            activateKeysetSignature = "fake-activate-keyset-sig",
            sealedDdkData = "FakeSealedData".encodeBase64().decodeBase64()
          )
        )
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()

      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()

      // W3 hardware goes directly to hardware descriptor validation (signed keys from activation)
      awaitItem().let {
        it.shouldBeTypeOf<BuildingHardwareDescriptorData>()
        it.accountIndex shouldBe 0u
        it.onSuccess(AppGlobalAuthKeyHwSignature(""))
      }

      // Persisting hw descriptor validation progress
      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.HwDescriptorValidated>()

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()
      delegatedDecryptionKeyService.uploadCalls!!.awaitItem()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("W1 hardware path skips descriptor validation") {
    val recovery = hardwareRecovery()
    delay(delayDuration)

    // Set up account config to use W1 hardware
    accountConfigService.setHardwareType(HardwareType.W1)

    stateMachine.test(
      props = props(recovery),
      turbineTimeout = 10.seconds
    ) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Rotate auth keys
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            csek = CsekFake,
            ssek = SsekFake,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().let {
        it.shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
      }

      awaitItem().shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofAndKeyTransferLostHwData>()
        it.nfcTransaction.onSuccess(
          ProofAndKeyTransferLostHwResult(
            hwProofOfPossession = HwFactorProofOfPossession("signed-token"),
            sealedDdkData = "FakeSealedData".encodeBase64().decodeBase64()
          )
        )
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()
        .spendingKeysets.shouldBe(listOf(SpendingKeysetMock))

      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()

      // W1 hardware with default firmware (1.2.3 >= 1.0.101) enters provisioning via unified state
      awaitItem().let {
        it.shouldBeTypeOf<ProvisioningAppAuthKeyToHardwareData>()
        it.nfcTransaction.onSuccess(Unit)
      }

      // Persisting hw descriptor validation progress
      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.HwDescriptorValidated>()

      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()
      delegatedDecryptionKeyService.uploadCalls!!.awaitItem()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("lost app recovery does not fetch DDK for only endorsed trusted contacts") {
    val recovery = recovery()
    delay(delayDuration)
    delegatedDecryptionKeyService.getSealedDelegatedDecryptionKeyDataResult =
      Err(Error("DDK should not be fetched without protected customers"))

    stateMachine.test(
      props = props(recovery),
      turbineTimeout = 10.seconds
    ) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcTransaction.onSuccess(
          SignedChallengeAndSeks(
            signedChallenge = fakeChallenge,
            csek = CsekFake,
            ssek = SsekFake,
            sealedCsek = SealedCsekFake,
            sealedSsek = SealedSsekFake
          )
        )
      }

      awaitItem().shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
      awaitItem().shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()
      awaitItem().shouldBeTypeOf<AwaitingProofAndKeyTransferLostAppData>()

      delegatedDecryptionKeyService.getSealedDelegatedDecryptionKeyDataCalls.shouldBe(0)

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("lost hardware recovery uses replacement telemetry over configured W3 account") {
    val recovery = hardwareRecovery()
    delay(delayDuration)

    accountConfigService.setHardwareType(HardwareType.W3)
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = createFirmwareDeviceInfo("2.0.0", hwRevision = "w1a-dvt"),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    stateMachine.test(
      props = props(recovery),
      turbineTimeout = 10.seconds
    ) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcSession.shouldBeTypeOf<RecoveryNfcSession.Standard<*>>()
      }

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("lost app recovery uses hardware telemetry over configured W3 default") {
    val recovery = recovery()
    delay(delayDuration)

    accountConfigService.setHardwareType(HardwareType.W3)
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = createFirmwareDeviceInfo("2.0.0", hwRevision = "w1a-dvt"),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    stateMachine.test(
      props = props(recovery),
      turbineTimeout = 10.seconds
    ) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
        it.nfcSession.shouldBeTypeOf<RecoveryNfcSession.Standard<*>>()
      }

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("hardware descriptor validation fails and retries") {
    val recovery = hardwareRecovery()
    val w3DeviceInfo = createFirmwareDeviceInfo("2.0.0", hwRevision = "w3a-core-evt")
    delay(delayDuration)

    // Set up W3 hardware: firmware reports W3 hwRevision + private wallet keyset
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = w3DeviceInfo,
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )
    nfcCommandsMock.deviceInfoResult = w3DeviceInfo
    descriptorBackupService.getNextAccountIndexResult = Ok(1u)
    delayNotifyService.createSpendingKeysetResult = com.github.michaelbull.result.Ok(F8eSpendingKeysetPrivateWalletMock)
    // activateSpendingKeyset returns signed keys for W3
    delayNotifyService.activateSpendingKeysetResult = com.github.michaelbull.result.Ok(SignedKeysetVerificationResponseMock)

    stateMachine.test(
      props = props(recovery),
      turbineTimeout = 10.seconds
    ) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Rotate auth keys (W3 confirmable)
      awaitW3Tap1Data().let {
        it.nfcSession.confirmableRunSessionAndOnSuccess(nfcCommandsMock)
        nfcCommandsMock.signChallengeAndSealSeksCalls.awaitItem()
      }

      awaitItem().shouldBeTypeOf<RotatingAuthKeysWithF8eData>()

      awaitItem().shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofAndKeyTransferLostHwData>()
        it.nfcSession.confirmableOnSuccess(
          RecoveryAuthorizeLostHwResult(
            descriptorBackupsSignature = "fake-descriptor-backups-sig",
            activateKeysetSignature = "fake-activate-keyset-sig",
            sealedDdkData = "FakeSealedData".encodeBase64().decodeBase64()
          )
        )
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()

      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()

      // W3 hardware goes to descriptor validation, then fails
      awaitItem().let {
        it.shouldBeTypeOf<BuildingHardwareDescriptorData>()
        it.onFailure(Error("Simulated descriptor validation failure"))
      }

      // Should fail and enter failed state, then retry
      awaitItem().let {
        it.shouldBeTypeOf<FailedToBuildHardwareDescriptorData>()
        it.onRetry()
      }

      // Retry goes back to PreparingProofAndKeyTransferData (two-tap flow)
      awaitItem().let {
        it.shouldBeTypeOf<PreparingProofAndKeyTransferData>()
      }

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("W3 hardware fails when signed keys are missing from activation response") {
    val recovery = hardwareRecovery()
    val w3DeviceInfo = createFirmwareDeviceInfo("2.0.0", hwRevision = "w3a-core-evt")
    delay(delayDuration)

    // Set up W3 hardware: firmware reports W3 hwRevision + private wallet keyset
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = w3DeviceInfo,
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )
    nfcCommandsMock.deviceInfoResult = w3DeviceInfo
    descriptorBackupService.getNextAccountIndexResult = Ok(1u)
    delayNotifyService.createSpendingKeysetResult = com.github.michaelbull.result.Ok(F8eSpendingKeysetPrivateWalletMock)
    // activateSpendingKeyset returns null (no signed keys) - should fail for W3
    delayNotifyService.activateSpendingKeysetResult = com.github.michaelbull.result.Ok(null)

    stateMachine.test(
      props = props(recovery),
      turbineTimeout = 10.seconds
    ) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Rotate auth keys (W3 confirmable)
      awaitW3Tap1Data().let {
        it.nfcSession.confirmableRunSessionAndOnSuccess(nfcCommandsMock)
        nfcCommandsMock.signChallengeAndSealSeksCalls.awaitItem()
      }

      awaitItem().shouldBeTypeOf<RotatingAuthKeysWithF8eData>()

      awaitItem().shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofAndKeyTransferLostHwData>()
        it.nfcSession.confirmableOnSuccess(
          RecoveryAuthorizeLostHwResult(
            descriptorBackupsSignature = "fake-descriptor-backups-sig",
            activateKeysetSignature = "fake-activate-keyset-sig",
            sealedDdkData = "FakeSealedData".encodeBase64().decodeBase64()
          )
        )
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()

      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()

      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()

      // W3 with null signed keys should fail, not skip to DDK backup
      awaitItem().let {
        it.shouldBeTypeOf<FailedToActivateSpendingKeysetData>()
        it.cause.message shouldBe "W3 keyset activation did not return signed keys for descriptor validation"
      }

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("W3 tap 1 cancel returns to ReadyToCompleteRecoveryData") {
    val recovery = hardwareRecovery()
    val w3DeviceInfo = createFirmwareDeviceInfo("2.0.0", hwRevision = "w3a-core-evt")
    delay(delayDuration)

    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = w3DeviceInfo,
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )
    nfcCommandsMock.deviceInfoResult = w3DeviceInfo
    descriptorBackupService.getNextAccountIndexResult = Ok(1u)
    stateMachine.test(
      props = props(recovery),
      turbineTimeout = 10.seconds
    ) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      awaitW3Tap1Data().let {
        it.nfcSession.confirmableOnCancel()
      }

      awaitItem().shouldBeTypeOf<ReadyToCompleteRecoveryData>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("W3 tap 2 cancel returns to PreparingProofAndKeyTransferData") {
    val recovery = hardwareRecovery()
    val w3DeviceInfo = createFirmwareDeviceInfo("2.0.0", hwRevision = "w3a-core-evt")
    delay(delayDuration)

    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = w3DeviceInfo,
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )
    nfcCommandsMock.deviceInfoResult = w3DeviceInfo
    descriptorBackupService.getNextAccountIndexResult = Ok(1u)
    delayNotifyService.createSpendingKeysetResult = com.github.michaelbull.result.Ok(F8eSpendingKeysetPrivateWalletMock)

    stateMachine.test(
      props = props(recovery),
      turbineTimeout = 10.seconds
    ) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Tap 1
      awaitW3Tap1Data().let {
        it.nfcSession.confirmableRunSessionAndOnSuccess(nfcCommandsMock)
        nfcCommandsMock.signChallengeAndSealSeksCalls.awaitItem()
      }

      awaitItem().shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
      awaitItem().shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      // Tap 2 — cancel
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofAndKeyTransferLostHwData>()
        it.nfcSession.confirmableOnCancel()
      }

      // Should return to preparing state for retry
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("W3 lost app recovery path exercises descriptor backups") {
    val recovery = recovery()
    val w3DeviceInfo = createFirmwareDeviceInfo("2.0.0", hwRevision = "w3a-core-evt")
    delay(delayDuration)

    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = w3DeviceInfo,
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )
    nfcCommandsMock.deviceInfoResult = w3DeviceInfo
    descriptorBackupService.getNextAccountIndexResult = Ok(1u)
    delayNotifyService.createSpendingKeysetResult = com.github.michaelbull.result.Ok(F8eSpendingKeysetPrivateWalletMock)
    delayNotifyService.activateSpendingKeysetResult = com.github.michaelbull.result.Ok(SignedKeysetVerificationResponseMock)

    stateMachine.test(
      props = props(recovery),
      turbineTimeout = 10.seconds
    ) {
      awaitItem().let {
        it.shouldBeTypeOf<ReadyToCompleteRecoveryData>()
        it.startComplete()
      }

      // Tap 1
      awaitW3Tap1Data().let {
        it.nfcSession.confirmableRunSessionAndOnSuccess(nfcCommandsMock)
        nfcCommandsMock.signChallengeAndSealSeksCalls.awaitItem()
      }

      awaitItem().shouldBeTypeOf<RotatingAuthKeysWithF8eData>()
      awaitItem().shouldBeTypeOf<CreatingSpendingKeysWithF8EData>()
      awaitItem().shouldBeTypeOf<PreparingProofAndKeyTransferData>()

      // Tap 2 — Lost App
      awaitItem().let {
        it.shouldBeTypeOf<AwaitingProofAndKeyTransferLostAppData>()
        it.nfcSession.confirmableOnSuccess(
          build.wallet.nfc.platform.RecoveryAuthorizeLostAppResult(
            descriptorBackupsSignature = "fake-descriptor-backups-sig",
            activateKeysetSignature = "fake-activate-keyset-sig",
            unsealedDdkData = null,
            unsealedSsek = null
          )
        )
      }

      awaitItem().shouldBeTypeOf<HandlingDescriptorEncryption>()
      awaitItem().shouldBeTypeOf<UploadingDescriptorBackupsData>()

      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.UploadedDescriptorBackups>()

      awaitItem().shouldBeTypeOf<ActivatingSpendingKeysetData>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("HwDescriptorValidated with sealedDdkData resumes DDK upload without NFC tap") {
    // Verifies the bug fix: when sealedDdkData is present in the restored checkpoint,
    // the state machine skips the NFC re-seal tap and goes straight to upload.
    val sealedDdk = "FakeSealedDdkBytes".encodeBase64().decodeBase64()!!
    val recovery = Recovery.StillRecovering.ServerIndependentRecovery.HwDescriptorValidated(
      f8eSpendingKeyset = F8eSpendingKeysetMock,
      fullAccountId = StillRecoveringInitiatedRecoveryMock.fullAccountId,
      appSpendingKey = StillRecoveringInitiatedRecoveryMock.appSpendingKey,
      appGlobalAuthKey = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKey,
      appRecoveryAuthKey = StillRecoveringInitiatedRecoveryMock.appRecoveryAuthKey,
      hardwareSpendingKey = StillRecoveringInitiatedRecoveryMock.hardwareSpendingKey,
      hardwareAuthKey = StillRecoveringInitiatedRecoveryMock.hardwareAuthKey,
      factorToRecover = Hardware,
      appGlobalAuthKeyHwSignature = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKeyHwSignature,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake,
      keysets = listOf(SpendingKeysetMock),
      originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2,
      sealedDdkData = sealedDdk
    )

    stateMachine.test(props(recovery)) {
      // Should immediately upload using the restored sealed DDK — no NFC tap needed
      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()
      delegatedDecryptionKeyService.uploadCalls!!.awaitItem()
      recoveryStatusService.setLocalRecoveryProgressCalls.awaitItem()
        .shouldBeTypeOf<LocalRecoveryAttemptProgress.DdkBackedUp>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("HwDescriptorValidated with null sealedDdkData falls back to NFC re-seal tap") {
    // Verifies backward compat: when sealedDdkData is null (old DB row, or W1 standalone-seal
    // path), the state machine falls back to requesting a new NFC tap to seal the DDK.
    val recovery = Recovery.StillRecovering.ServerIndependentRecovery.HwDescriptorValidated(
      f8eSpendingKeyset = F8eSpendingKeysetMock,
      fullAccountId = StillRecoveringInitiatedRecoveryMock.fullAccountId,
      appSpendingKey = StillRecoveringInitiatedRecoveryMock.appSpendingKey,
      appGlobalAuthKey = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKey,
      appRecoveryAuthKey = StillRecoveringInitiatedRecoveryMock.appRecoveryAuthKey,
      hardwareSpendingKey = StillRecoveringInitiatedRecoveryMock.hardwareSpendingKey,
      hardwareAuthKey = StillRecoveringInitiatedRecoveryMock.hardwareAuthKey,
      factorToRecover = Hardware,
      appGlobalAuthKeyHwSignature = StillRecoveringInitiatedRecoveryMock.appGlobalAuthKeyHwSignature,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake,
      keysets = listOf(SpendingKeysetMock),
      originalAppGlobalAuthKey = AppGlobalAuthPublicKeyMock2,
      sealedDdkData = null
    )

    stateMachine.test(props(recovery)) {
      // Shows DDK backup loading while fetching keypair
      awaitItem().shouldBeTypeOf<PerformingDdkBackupData>()

      // Then prompts for NFC tap to seal the DDK
      awaitItem().shouldBeTypeOf<SealingDelegatedDecryptionKeyData>()

      cancelAndIgnoreRemainingEvents()
    }
  }
})

/**
 * Convenience accessor for W1 (Standard) NFC tests.
 * Casts the nfcSession to Standard and returns the inner NfcTransaction.
 */
@Suppress("UNCHECKED_CAST")
private val AwaitingChallengeAndCsekSignedWithHardwareData.nfcTransaction: NfcTransaction<Any?>
  get() = (nfcSession as RecoveryNfcSession.Standard<Any?>).transaction

@Suppress("UNCHECKED_CAST")
private val AwaitingProofAndKeyTransferLostAppData.nfcTransaction: NfcTransaction<Any?>
  get() = (nfcSession as RecoveryNfcSession.Standard<Any?>).transaction

@Suppress("UNCHECKED_CAST")
private val AwaitingProofAndKeyTransferLostHwData.nfcTransaction: NfcTransaction<Any?>
  get() = (nfcSession as RecoveryNfcSession.Standard<Any?>).transaction

/**
 * Convenience accessor for W3 (Confirmable) NFC tests.
 * Runs the session lambda first (to initialize any captured state like lateinit vars),
 * then invokes onSuccess with the Completed result from the session.
 * Falls back to directly calling onSuccess with [fallbackResult] if provided.
 */
@Suppress("UNCHECKED_CAST")
private suspend fun <T> RecoveryNfcSession.confirmableOnSuccess(result: T) {
  val confirmable = this as RecoveryNfcSession.Confirmable<T>
  confirmable.onSuccess(result)
}

/**
 * Runs the session lambda with fakes (initializing any captured state), then calls
 * onSuccess with the Completed result. Use this for Confirmable sessions that share
 * state between session and onSuccess (e.g. W3SignChallengeAndSealSeks).
 */
@Suppress("UNCHECKED_CAST")
private suspend fun RecoveryNfcSession.confirmableRunSessionAndOnSuccess(nfcCommands: NfcCommands) {
  val confirmable = this as RecoveryNfcSession.Confirmable<Any?>
  val interaction = confirmable.session(NfcSessionFake(), nfcCommands)
  val completed = interaction as HardwareInteraction.Completed<Any?>
  confirmable.onSuccess(completed.result)
}

private fun RecoveryNfcSession.confirmableOnCancel() {
  val confirmable = this as RecoveryNfcSession.Confirmable<*>
  confirmable.onCancel()
}

private suspend fun ReceiveTurbine<RecoveryInProgressData>.awaitW3Tap1Data():
  AwaitingChallengeAndCsekSignedWithHardwareData {
  val item = awaitItem()
  return when (item) {
    is AwaitingChallengeAndCsekSignedWithHardwareData -> item
    is ReadyToCompleteRecoveryData -> awaitItem().shouldBeTypeOf<AwaitingChallengeAndCsekSignedWithHardwareData>()
    else -> error("Expected W3 tap 1 prompt, got $item")
  }
}

private fun createFirmwareDeviceInfo(
  version: String,
  hwRevision: String = "w1a-dvt",
) = FirmwareDeviceInfo(
  version = version,
  serial = "test-serial",
  swType = "test",
  hwRevision = hwRevision,
  activeSlot = FirmwareMetadata.FirmwareSlot.A,
  batteryCharge = 50.0,
  vCell = 1000,
  avgCurrentMa = 100,
  batteryCycles = 10,
  secureBootConfig = SecureBootConfig.DEV,
  timeRetrieved = Instant.fromEpochSeconds(1234567890).epochSeconds,
  bioMatchStats = null,
  mcuInfo = emptyList()
)
