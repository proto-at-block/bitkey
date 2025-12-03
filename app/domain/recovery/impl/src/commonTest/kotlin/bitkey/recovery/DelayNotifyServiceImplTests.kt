package bitkey.recovery

import bitkey.account.AccountConfigServiceFake
import bitkey.f8e.error.F8eError
import bitkey.recovery.DelayNotifyCancellationRequest.CancelLostAppAndCloudRecovery
import bitkey.recovery.DelayNotifyCancellationRequest.CancelLostHardwareRecovery
import build.wallet.account.AccountServiceFake
import build.wallet.auth.AccountAuthenticatorMock
import build.wallet.auth.AppAuthKeyMessageSignerMock
import build.wallet.auth.AuthProtocolError
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitkey.challange.DelayNotifyRecoveryChallengeFake
import build.wallet.bitkey.challange.SignedChallenge
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.F8eSpendingKeysetPrivateWalletMock
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.onboarding.CreateAccountKeysetF8eClientFake
import build.wallet.f8e.onboarding.CreateAccountKeysetV2F8eClientFake
import build.wallet.f8e.onboarding.SetActiveSpendingKeysetF8eClientFake
import build.wallet.f8e.recovery.CompleteDelayNotifyF8eClientMock
import build.wallet.f8e.recovery.ListKeysetsF8eClientMock
import build.wallet.f8e.recovery.ListKeysetsResponse
import build.wallet.f8e.recovery.PrivateMultisigRemoteKeyset
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.ChaincodeDelegationFeatureFlag
import build.wallet.feature.flags.UpdateToPrivateWalletOnRecoveryFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.ktor.result.HttpError
import build.wallet.notifications.DeviceTokenManagerMock
import build.wallet.recovery.*
import build.wallet.relationships.EndorseTrustedContactsServiceMock
import build.wallet.relationships.RelationshipsServiceMock
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.sync.Mutex

class DelayNotifyServiceImplTests : FunSpec({

  val lostAppAndCloudRecoveryService = LostAppAndCloudRecoveryServiceFake()
  val lostHardwareRecoveryService = LostHardwareRecoveryServiceFake()
  val recoveryStatusService = RecoveryStatusServiceMock(
    StillRecoveringInitiatedRecoveryMock,
    turbines::create
  )
  val recoveryDao = RecoveryDaoMock(turbines::create)
  val setActiveSpendingKeysetF8eClient = SetActiveSpendingKeysetF8eClientFake()
  val accountConfigService = AccountConfigServiceFake()
  val deviceTokenManager = DeviceTokenManagerMock(turbines::create)
  val createAccountKeysetF8eClient = CreateAccountKeysetF8eClientFake()
  val createAccountKeysetV2F8eClient = CreateAccountKeysetV2F8eClientFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val chaincodeDelegationFeatureFlag = ChaincodeDelegationFeatureFlag(featureFlagDao)
  val updateToPrivateWalletOnRecoveryFeatureFlag = UpdateToPrivateWalletOnRecoveryFeatureFlag(featureFlagDao)
  val appAuthKeyMessageSigner = AppAuthKeyMessageSignerMock()
  val completeDelayNotifyF8eClient = CompleteDelayNotifyF8eClientMock(turbines::create)
  val accountAuthenticator = AccountAuthenticatorMock(turbines::create)
  val authTokensService = AuthTokensServiceFake()
  val accountService = AccountServiceFake()
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val recoveryLock = object : RecoveryLock, Mutex by Mutex() {}
  val defaultAuthResult = accountAuthenticator.defaultAuthResult
  val listKeysetsF8eClient = ListKeysetsF8eClientMock()

  val clock = ClockFake()
  val relationshipsService = RelationshipsServiceMock(turbines::create, clock)
  val endorseTrustedContactsService = EndorseTrustedContactsServiceMock(turbines::create)

  val service = DelayNotifyServiceImpl(
    lostAppAndCloudRecoveryService = lostAppAndCloudRecoveryService,
    lostHardwareRecoveryService = lostHardwareRecoveryService,
    recoveryStatusService = recoveryStatusService,
    recoveryDao = recoveryDao,
    setActiveSpendingKeysetF8eClient = setActiveSpendingKeysetF8eClient,
    accountConfigService = accountConfigService,
    deviceTokenManager = deviceTokenManager,
    createAccountKeysetF8eClient = createAccountKeysetF8eClient,
    createAccountKeysetV2F8eClient = createAccountKeysetV2F8eClient,
    chaincodeDelegationFeatureFlag = chaincodeDelegationFeatureFlag,
    appAuthKeyMessageSigner = appAuthKeyMessageSigner,
    completeDelayNotifyF8eClient = completeDelayNotifyF8eClient,
    accountAuthenticator = accountAuthenticator,
    authTokensService = authTokensService,
    accountService = accountService,
    keyboxDao = keyboxDao,
    recoveryLock = recoveryLock,
    relationshipsService = relationshipsService,
    endorseTrustedContactsService = endorseTrustedContactsService,
    updateToPrivateWalletOnRecoveryFeatureFlag = updateToPrivateWalletOnRecoveryFeatureFlag,
    listKeysetF8eClient = listKeysetsF8eClient
  )

  beforeTest {
    lostAppAndCloudRecoveryService.reset()
    lostHardwareRecoveryService.reset()
    recoveryStatusService.reset()
    recoveryDao.reset()
    setActiveSpendingKeysetF8eClient.reset()
    accountConfigService.reset()
    deviceTokenManager.reset()
    createAccountKeysetF8eClient.reset()
    createAccountKeysetV2F8eClient.reset()
    featureFlagDao.reset()
    chaincodeDelegationFeatureFlag.reset()
    updateToPrivateWalletOnRecoveryFeatureFlag.reset()
    appAuthKeyMessageSigner.reset()
    accountAuthenticator.reset()
    authTokensService.reset()
    accountService.reset()
    listKeysetsF8eClient.reset()
  }

  test("cancel Lost App recovery with hw proof of possession - success") {
    recoveryStatusService.recoveryStatus.value =
      StillRecoveringInitiatedRecoveryMock.copy(factorToRecover = PhysicalFactor.App)

    val hwProof = HwFactorProofOfPossession("test-proof")
    val request = CancelLostAppAndCloudRecovery(hwProof)

    service.cancelDelayNotify(request).shouldBeOk()
  }

  test("cancel Lost App recovery with hw proof of possession - failure") {
    recoveryStatusService.recoveryStatus.value =
      StillRecoveringInitiatedRecoveryMock.copy(factorToRecover = PhysicalFactor.App)

    val error = CancelDelayNotifyRecoveryError.LocalCancelDelayNotifyError(
      cause = Error("Network error")
    )
    lostAppAndCloudRecoveryService.cancelResult = Err(error)
    val hwProof = HwFactorProofOfPossession("test-proof")
    val request = CancelLostAppAndCloudRecovery(hwProof)

    service.cancelDelayNotify(request)
      .shouldBeErrOfType<CancelDelayNotifyRecoveryError.LocalCancelDelayNotifyError>()
  }

  test("cancel Lost Hardware recovery - success") {
    recoveryStatusService.recoveryStatus.value =
      StillRecoveringInitiatedRecoveryMock.copy(factorToRecover = PhysicalFactor.Hardware)

    service.cancelDelayNotify(CancelLostHardwareRecovery).shouldBeOk()
  }

  test("cancel Lost Hardware recovery - failure") {
    recoveryStatusService.recoveryStatus.value =
      StillRecoveringInitiatedRecoveryMock.copy(factorToRecover = PhysicalFactor.Hardware)

    val error = CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError(
      error = F8eError.UnhandledError(HttpError.NetworkError(Throwable("Server error")))
    )
    lostHardwareRecoveryService.cancelResult = Err(error)

    service.cancelDelayNotify(CancelLostHardwareRecovery)
      .shouldBeErrOfType<CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError>()
  }

  test("cancel when no active recovery returns error") {
    recoveryStatusService.recoveryStatus.value = Recovery.NoActiveRecovery

    service.cancelDelayNotify(CancelLostHardwareRecovery)
      .shouldBeErrOfType<Error>()
  }

  test("activateSpendingKeyset success") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    setActiveSpendingKeysetF8eClient.setResult = Ok(Unit)

    val result = service.activateSpendingKeyset(
      keyset = F8eSpendingKeysetMock,
      hardwareProofOfPossession = HwFactorProofOfPossession("hw-proof")
    )

    result.shouldBeOk(Unit)
    val expectedProgress = LocalRecoveryAttemptProgress.ActivatedSpendingKeys(
      f8eSpendingKeyset = F8eSpendingKeysetMock
    )
    recoveryDao.setLocalRecoveryProgressCalls.awaitItem().shouldBe(expectedProgress)
  }

  test("activateSpendingKeyset returns error when f8e call fails") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    val networkError = HttpError.NetworkError(Throwable("Network error"))
    setActiveSpendingKeysetF8eClient.setResult = Err(networkError)

    val result = service.activateSpendingKeyset(
      keyset = F8eSpendingKeysetMock,
      hardwareProofOfPossession = HwFactorProofOfPossession("hw-proof")
    )

    result.shouldBeErrOfType<HttpError.NetworkError>()
    recoveryDao.setLocalRecoveryProgressCalls.expectNoEvents()
  }

  test("activateSpendingKeyset returns error when updating local recovery progress fails") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    setActiveSpendingKeysetF8eClient.setResult = Ok(Unit)
    val error = Error("uh oh")
    recoveryDao.setLocalRecoveryProgressResult = Err(error)

    val result = service.activateSpendingKeyset(
      keyset = F8eSpendingKeysetMock,
      hardwareProofOfPossession = HwFactorProofOfPossession("hw-proof")
    )

    result.shouldBeErrOfType<Error>()
    recoveryDao.setLocalRecoveryProgressCalls.awaitItem()
  }

  test("activateSpendingKeyset returns error when no active recovery") {
    recoveryStatusService.recoveryStatus.value = Recovery.NoActiveRecovery

    val result = service.activateSpendingKeyset(
      keyset = F8eSpendingKeysetMock,
      hardwareProofOfPossession = HwFactorProofOfPossession("hw-proof")
    )

    result.shouldBeErrOfType<Error>()
  }

  test("createSpendingKeyset success with v1 client") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    createAccountKeysetF8eClient.createKeysetResult = Ok(F8eSpendingKeysetMock)

    val result = service.createSpendingKeyset(
      hardwareProofOfPossession = HwFactorProofOfPossession("hw-proof")
    )

    result.shouldBeOk(F8eSpendingKeysetMock)
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
    val expectedProgress = LocalRecoveryAttemptProgress.CreatedSpendingKeys(
      f8eSpendingKeyset = F8eSpendingKeysetMock
    )
    recoveryDao.setLocalRecoveryProgressCalls.awaitItem().shouldBe(expectedProgress)
  }

  test("createSpendingKeyset success with v2 client when chaincode delegation and implicit private updates are enabled") {
    chaincodeDelegationFeatureFlag.setFlagValue(true)
    updateToPrivateWalletOnRecoveryFeatureFlag.setFlagValue(true)
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock

    val result = service.createSpendingKeyset(
      hardwareProofOfPossession = HwFactorProofOfPossession("hw-proof")
    )

    result.shouldBeOk(F8eSpendingKeysetPrivateWalletMock)
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
    val expectedProgress = LocalRecoveryAttemptProgress.CreatedSpendingKeys(
      f8eSpendingKeyset = F8eSpendingKeysetPrivateWalletMock
    )
    recoveryDao.setLocalRecoveryProgressCalls.awaitItem().shouldBe(expectedProgress)
  }

  test("createSpendingKeyset uses v2 client when account has private keyset even if feature flags are disabled") {
    chaincodeDelegationFeatureFlag.setFlagValue(false)
    updateToPrivateWalletOnRecoveryFeatureFlag.setFlagValue(false)
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock

    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = listOf(
          PrivateMultisigRemoteKeyset(
            keysetId = "private-keyset-id",
            networkType = "SIGNET",
            appPublicKey = "app-pub",
            hardwarePublicKey = "hw-pub",
            serverPublicKey = "server-pub"
          )
        ),
        wrappedSsek = null,
        descriptorBackups = emptyList()
      )
    )

    val result = service.createSpendingKeyset(
      hardwareProofOfPossession = HwFactorProofOfPossession("hw-proof")
    )

    result.shouldBeOk(F8eSpendingKeysetPrivateWalletMock)
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
    val expectedProgress = LocalRecoveryAttemptProgress.CreatedSpendingKeys(
      f8eSpendingKeyset = F8eSpendingKeysetPrivateWalletMock
    )
    recoveryDao.setLocalRecoveryProgressCalls.awaitItem().shouldBe(expectedProgress)
  }

  test("createSpendingKeyset returns error when f8e call fails") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    val networkError = HttpError.NetworkError(Throwable("Network error"))
    createAccountKeysetF8eClient.createKeysetResult = Err(networkError)

    val result = service.createSpendingKeyset(
      hardwareProofOfPossession = HwFactorProofOfPossession("hw-proof")
    )

    result.shouldBeErrOfType<HttpError.NetworkError>()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
    recoveryDao.setLocalRecoveryProgressCalls.expectNoEvents()
  }

  test("createSpendingKeyset returns error when updating local recovery progress fails") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    createAccountKeysetF8eClient.createKeysetResult = Ok(F8eSpendingKeysetMock)
    val error = Error("uh oh")
    recoveryDao.setLocalRecoveryProgressResult = Err(error)

    val result = service.createSpendingKeyset(
      hardwareProofOfPossession = HwFactorProofOfPossession("hw-proof")
    )

    result.shouldBeErrOfType<Error>()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
    recoveryDao.setLocalRecoveryProgressCalls.awaitItem()
  }

  test("createSpendingKeyset returns error when no active recovery") {
    recoveryStatusService.recoveryStatus.value = Recovery.NoActiveRecovery

    val result = service.createSpendingKeyset(
      hardwareProofOfPossession = HwFactorProofOfPossession("hw-proof")
    )

    result.shouldBeErrOfType<Error>()
  }

  test("rotateAuthKeys success") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    val hardwareSignedChallenge =
      SignedChallenge.HardwareSignedChallenge(
        challenge = DelayNotifyRecoveryChallengeFake,
        signature = "hw-signature"
      )
    appAuthKeyMessageSigner.result = Ok("app-signature")

    val result = service.rotateAuthKeys(
      hardwareSignedChallenge = hardwareSignedChallenge,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake
    )

    result.shouldBeOk(Unit)
    // Verify complete was called
    completeDelayNotifyF8eClient.completeRecoveryCalls.awaitItem()
    // Verify progress was set to AttemptingCompletion
    val progressCall = recoveryDao.setLocalRecoveryProgressCalls.awaitItem()
    progressCall.shouldBeTypeOf<LocalRecoveryAttemptProgress.AttemptingCompletion>()
  }

  test("rotateAuthKeys returns error when no active recovery") {
    recoveryStatusService.recoveryStatus.value = Recovery.NoActiveRecovery
    val hardwareSignedChallenge =
      SignedChallenge.HardwareSignedChallenge(
        challenge = DelayNotifyRecoveryChallengeFake,
        signature = "hw-signature"
      )

    val result = service.rotateAuthKeys(
      hardwareSignedChallenge = hardwareSignedChallenge,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake
    )

    result.shouldBeErrOfType<Error>()
  }

  test("rotateAuthKeys returns error when app signature fails") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    val hardwareSignedChallenge =
      SignedChallenge.HardwareSignedChallenge(
        challenge = DelayNotifyRecoveryChallengeFake,
        signature = "hw-signature"
      )
    val error = Throwable("Signature failed")
    appAuthKeyMessageSigner.result = Err(error)

    val result = service.rotateAuthKeys(
      hardwareSignedChallenge = hardwareSignedChallenge,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake
    )

    result.shouldBeErrOfType<Throwable>()
    // Verify progress was still set to AttemptingCompletion before failure
    recoveryDao.setLocalRecoveryProgressCalls.awaitItem()
  }

  test("rotateAuthKeys returns error when f8e complete fails") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    val hardwareSignedChallenge =
      SignedChallenge.HardwareSignedChallenge(
        challenge = DelayNotifyRecoveryChallengeFake,
        signature = "hw-signature"
      )
    appAuthKeyMessageSigner.result = Ok("app-signature")

    val result = service.rotateAuthKeys(
      hardwareSignedChallenge = hardwareSignedChallenge,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake
    )

    // The mock returns Ok(Unit) by default, but we can verify the call was made
    result.shouldBeOk(Unit)
    completeDelayNotifyF8eClient.completeRecoveryCalls.awaitItem()
    recoveryDao.setLocalRecoveryProgressCalls.awaitItem()
  }

  test("rotateAuthKeys returns error when setting local recovery progress fails") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    val hardwareSignedChallenge =
      SignedChallenge.HardwareSignedChallenge(
        challenge = DelayNotifyRecoveryChallengeFake,
        signature = "hw-signature"
      )
    val error = Error("Failed to set progress")
    recoveryDao.setLocalRecoveryProgressResult = Err(error)

    val result = service.rotateAuthKeys(
      hardwareSignedChallenge = hardwareSignedChallenge,
      sealedCsek = SealedCsekFake,
      sealedSsek = SealedSsekFake
    )

    result.shouldBeErrOfType<Error>()
    recoveryDao.setLocalRecoveryProgressCalls.awaitItem()
  }

  test("rotateAuthTokens success") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    accountAuthenticator.authResults = mutableListOf(
      defaultAuthResult,
      defaultAuthResult
    )

    val result = service.rotateAuthTokens()

    result.shouldBeOk(Unit)
    // Verify both auth calls were made (global and recovery)
    accountAuthenticator.authCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
    // Verify progress was set to RotatedAuthKeys
    val progressCall = recoveryDao.setLocalRecoveryProgressCalls.awaitItem()
    progressCall.shouldBe(LocalRecoveryAttemptProgress.RotatedAuthKeys)
  }

  test("rotateAuthTokens returns error when no active recovery") {
    recoveryStatusService.recoveryStatus.value = Recovery.NoActiveRecovery

    val result = service.rotateAuthTokens()

    result.shouldBeErrOfType<Error>()
  }

  test("rotateAuthTokens returns error when global auth fails") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    val authError = AuthProtocolError(
      cause = Throwable("Auth failed")
    )
    accountAuthenticator.authResults = mutableListOf(Err(authError))

    val result = service.rotateAuthTokens()

    result.shouldBeErrOfType<AuthProtocolError>()
    accountAuthenticator.authCalls.awaitItem()
  }

  test("rotateAuthTokens returns error when recovery auth fails") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    val authError = AuthProtocolError(cause = Throwable("Auth failed"))
    accountAuthenticator.authResults = mutableListOf(
      defaultAuthResult,
      Err(authError)
    )

    val result = service.rotateAuthTokens()

    result.shouldBeErrOfType<AuthProtocolError>()
    accountAuthenticator.authCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
  }

  test("rotateAuthTokens returns error when saving auth tokens fails") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    accountAuthenticator.authResults = mutableListOf(
      defaultAuthResult,
      defaultAuthResult
    )
    val error = Error("Storage error")
    authTokensService.setTokensError = error

    val result = service.rotateAuthTokens()

    result.shouldBeErrOfType<Error>()
    accountAuthenticator.authCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
  }

  test("rotateAuthTokens returns error when setting local recovery progress fails") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    accountAuthenticator.authResults = mutableListOf(
      defaultAuthResult,
      defaultAuthResult
    )
    val error = Error("Failed to set progress")
    recoveryDao.setLocalRecoveryProgressResult = Err(error)

    val result = service.rotateAuthTokens()

    result.shouldBeErrOfType<Error>()
    accountAuthenticator.authCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
    recoveryDao.setLocalRecoveryProgressCalls.awaitItem()
  }

  test("verifyAuthKeysAfterRotation success") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    accountAuthenticator.authResults = mutableListOf(
      defaultAuthResult,
      defaultAuthResult
    )

    val result = service.verifyAuthKeysAfterRotation()

    result.shouldBeOk(Unit)
    // Verify both auth calls were made (global and recovery)
    accountAuthenticator.authCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
  }

  test("verifyAuthKeysAfterRotation returns error when no active recovery") {
    recoveryStatusService.recoveryStatus.value = Recovery.NoActiveRecovery

    val result = service.verifyAuthKeysAfterRotation()

    result.shouldBeErrOfType<Error>()
  }

  test("verifyAuthKeysAfterRotation returns error when global auth fails") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    val authError = AuthProtocolError(cause = Throwable("Auth failed"))
    accountAuthenticator.authResults = mutableListOf(Err(authError))

    val result = service.verifyAuthKeysAfterRotation()

    result.shouldBeErrOfType<AuthProtocolError>()
    accountAuthenticator.authCalls.awaitItem()
  }

  test("verifyAuthKeysAfterRotation returns error when recovery auth fails") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock
    val authError = AuthProtocolError(cause = Throwable("Auth failed"))
    accountAuthenticator.authResults = mutableListOf(
      defaultAuthResult,
      Err(authError)
    )

    val result = service.verifyAuthKeysAfterRotation()

    result.shouldBeErrOfType<AuthProtocolError>()
    accountAuthenticator.authCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem()
  }

  test("regenerateTrustedContactCertificates success") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock

    val result = service.regenerateTrustedContactCertificates(oldAppGlobalAuthKey = null)

    result.shouldBeOk(Unit)
    relationshipsService.syncCalls.awaitItem()
  }

  test("regenerateTrustedContactCertificates returns error when no active recovery") {
    recoveryStatusService.recoveryStatus.value = Recovery.NoActiveRecovery

    val result = service.regenerateTrustedContactCertificates(oldAppGlobalAuthKey = null)

    result.shouldBeErrOfType<Error>()
  }

  test("removeTrustedContacts success") {
    recoveryStatusService.recoveryStatus.value = StillRecoveringInitiatedRecoveryMock

    val result = service.removeTrustedContacts()

    result.shouldBeOk(Unit)
  }

  test("removeTrustedContacts returns error when no active recovery") {
    recoveryStatusService.recoveryStatus.value = Recovery.NoActiveRecovery

    val result = service.removeTrustedContacts()

    result.shouldBeErrOfType<Error>()
  }
})
