package build.wallet.auth

import build.wallet.auth.AuthKeyRotationFailure.Unexpected
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.cloud.backup.CloudBackupRepositoryFake
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.backup.FullAccountCloudBackupCreator
import build.wallet.cloud.backup.FullAccountCloudBackupCreatorMock
import build.wallet.cloud.backup.local.CloudBackupDaoFake
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.RotateAuthKeysF8eClientMock
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.ktor.result.HttpBodyError
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.isClientError
import build.wallet.ktor.result.isServerError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.relationships.EndorseTrustedContactsServiceMock
import build.wallet.relationships.RelationshipsServiceMock
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.exhaustive
import io.kotest.property.exhaustive.plus
import io.ktor.http.HttpStatusCode

class FullAccountAuthKeyRotationServiceImplTests : FunSpec({

  val clock = ClockFake()
  val authKeyRotationAttemptDao = AuthKeyRotationAttemptDaoMock(turbines::create)
  val rotateAuthKeysF8eClient = RotateAuthKeysF8eClientMock(turbines::create)
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val accountAuthenticator = AccountAuthenticatorMock(turbines::create)
  val relationshipsService = RelationshipsServiceMock(turbines::create, clock)
  val trustedContactKeyAuthenticator = EndorseTrustedContactsServiceMock(turbines::create)
  val cloudBackupDao = CloudBackupDaoFake()
  val fullAccountCloudBackupCreator = FullAccountCloudBackupCreatorMock(turbines::create)
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val cloudBackupRepository = CloudBackupRepositoryFake()
  val cloudAccount = CloudAccountMock("cloudInstanceId")

  val fullAccountAuthKeyRotationService = FullAccountAuthKeyRotationServiceImpl(
    authKeyRotationAttemptDao = authKeyRotationAttemptDao,
    rotateAuthKeysF8eClient = rotateAuthKeysF8eClient,
    keyboxDao = keyboxDao,
    accountAuthenticator = accountAuthenticator,
    cloudBackupDao = cloudBackupDao,
    fullAccountCloudBackupCreator = fullAccountCloudBackupCreator,
    cloudStoreAccountRepository = cloudStoreAccountRepository,
    cloudBackupRepository = cloudBackupRepository,
    relationshipsService = relationshipsService,
    endorseTrustedContactsService = trustedContactKeyAuthenticator
  )

  beforeEach {
    keyboxDao.reset()
    rotateAuthKeysF8eClient.reset()
    cloudBackupDao.reset()
    fullAccountCloudBackupCreator.reset()
    cloudStoreAccountRepository.reset()
    cloudBackupRepository.reset()
  }

  val generatedGlobalAuthKey =
    PublicKey<AppGlobalAuthKey>("new-fake-auth-dpub")
  val generatedRecoveryAuthKey =
    PublicKey<AppRecoveryAuthKey>("new-fake-recovery-dpub")
  val generatedGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("new-fake-hw-signature")
  val generateAppAuthKeys = AppAuthPublicKeys(
    appGlobalAuthPublicKey = generatedGlobalAuthKey,
    appRecoveryAuthPublicKey = generatedRecoveryAuthKey,
    appGlobalAuthKeyHwSignature = generatedGlobalAuthKeyHwSignature
  )

  test("start new auth key rotation") {
    keyboxDao.rotateKeyboxResult = Ok(
      KeyboxMock.copy(
        activeAppKeyBundle = KeyboxMock.activeAppKeyBundle.copy(
          authKey = generatedGlobalAuthKey,
          recoveryAuthKey = generatedRecoveryAuthKey
        ),
        appGlobalAuthKeyHwSignature = generatedGlobalAuthKeyHwSignature
      )
    )
    cloudBackupDao.set(
      FullAccountMock.accountId.serverId,
      CloudBackupV2WithFullAccountMock
    )
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
    fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)

    accountAuthenticator.authResults = mutableListOf(
      // New global key validation
      authSuccessResponse,
      // New recovery key validation
      authSuccessResponse
    )

    val request = AuthKeyRotationRequest.Start(
      hwFactorProofOfPossession = HwFactorProofOfPossession("signed-token"),
      hwSignedAccountId = "signed-account-id",
      hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
      newKeys = generateAppAuthKeys
    )
    val result = fullAccountAuthKeyRotationService.startOrResumeAuthKeyRotation(
      request = request,
      account = FullAccountMock
    )
    result.shouldBeOk()

    authKeyRotationAttemptDao.getAuthKeyRotationAttemptStateCalls.expectNoEvents()
    authKeyRotationAttemptDao.setAuthKeysWrittenCalls.awaitItem()
    authKeyRotationAttemptDao.clearCalls.awaitItem()
    rotateAuthKeysF8eClient.rotateKeysetCalls.awaitItem()
    keyboxDao.rotateAuthKeysCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem() shouldBe generatedGlobalAuthKey
    accountAuthenticator.authCalls.awaitItem() shouldBe generatedRecoveryAuthKey
    accountAuthenticator.authResults.shouldBeEmpty()
    fullAccountCloudBackupCreator.createCalls.awaitItem()

    relationshipsService.syncCalls.awaitItem()
  }

  test("resume auth key rotation") {
    keyboxDao.rotateKeyboxResult = Ok(
      KeyboxMock.copy(
        activeAppKeyBundle = KeyboxMock.activeAppKeyBundle.copy(
          authKey = generateAppAuthKeys.appGlobalAuthPublicKey,
          recoveryAuthKey = generateAppAuthKeys.appRecoveryAuthPublicKey
        ),
        appGlobalAuthKeyHwSignature = generateAppAuthKeys.appGlobalAuthKeyHwSignature
      )
    )
    cloudBackupDao.set(
      FullAccountMock.accountId.serverId,
      CloudBackupV2WithFullAccountMock
    )
    cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
    fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)

    accountAuthenticator.authResults = mutableListOf(
      // New global key validation
      Ok(AccountAuthenticator.AuthData(FullAccountIdMock.serverId, AccountAuthTokensMock)),
      // New recovery key validation
      Ok(AccountAuthenticator.AuthData(FullAccountIdMock.serverId, AccountAuthTokensMock))
    )

    val request = AuthKeyRotationRequest.Resume(newKeys = generateAppAuthKeys)
    val result = fullAccountAuthKeyRotationService.startOrResumeAuthKeyRotation(
      request = request,
      account = FullAccountMock
    )
    result.shouldBeOk()

    authKeyRotationAttemptDao.setAuthKeysWrittenCalls.expectNoEvents()
    authKeyRotationAttemptDao.clearCalls.awaitItem()
    keyboxDao.rotateAuthKeysCalls.awaitItem()

    accountAuthenticator.authCalls.awaitItem() shouldBe request.newKeys.appGlobalAuthPublicKey
    accountAuthenticator.authCalls.awaitItem() shouldBe request.newKeys.appRecoveryAuthPublicKey
    accountAuthenticator.authResults.shouldBeEmpty()

    fullAccountCloudBackupCreator.createCalls.awaitItem()
    relationshipsService.syncCalls.awaitItem()
  }

  context("with successfully generated new keys") {
    beforeEach {
      keyboxDao.rotateKeyboxResult = Ok(
        KeyboxMock.copy(
          activeAppKeyBundle = KeyboxMock.activeAppKeyBundle.copy(
            authKey = generatedGlobalAuthKey,
            recoveryAuthKey = generatedRecoveryAuthKey
          ),
          appGlobalAuthKeyHwSignature = generatedGlobalAuthKeyHwSignature
        )
      )
    }

    test("cloud backup succeeds after successful key rotation") {
      cloudBackupDao.set(
        FullAccountMock.accountId.serverId,
        CloudBackupV2WithFullAccountMock
      )
      cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
      fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)

      accountAuthenticator.authResults = mutableListOf(
        // New global key validation
        Ok(AccountAuthenticator.AuthData(FullAccountIdMock.serverId, AccountAuthTokensMock)),
        // New recovery key validation
        Ok(AccountAuthenticator.AuthData(FullAccountIdMock.serverId, AccountAuthTokensMock))
      )

      val request = AuthKeyRotationRequest.Start(
        hwFactorProofOfPossession = HwFactorProofOfPossession("signed-token"),
        hwSignedAccountId = "signed-account-id",
        hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
        newKeys = generateAppAuthKeys
      )
      val result = fullAccountAuthKeyRotationService.startOrResumeAuthKeyRotation(
        request = request,
        account = FullAccountMock
      )
      result.shouldBeOk()

      authKeyRotationAttemptDao.getAuthKeyRotationAttemptStateCalls.expectNoEvents()
      authKeyRotationAttemptDao.setAuthKeysWrittenCalls.awaitItem()
      authKeyRotationAttemptDao.clearCalls.awaitItem()
      rotateAuthKeysF8eClient.rotateKeysetCalls.awaitItem()
      keyboxDao.rotateAuthKeysCalls.awaitItem()
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedGlobalAuthKey
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedRecoveryAuthKey
      accountAuthenticator.authResults.shouldBeEmpty()
      fullAccountCloudBackupCreator.createCalls.awaitItem()

      relationshipsService.syncCalls.awaitItem()
    }

    test("cloud backup failure causes rotation to fail") {
      cloudBackupDao.set(
        FullAccountMock.accountId.serverId,
        CloudBackupV2WithFullAccountMock
      )
      cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
      fullAccountCloudBackupCreator.backupResult = Err(
        FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.CsekMissing
      )

      accountAuthenticator.authResults = mutableListOf(
        // New global key validation
        Ok(AccountAuthenticator.AuthData(FullAccountIdMock.serverId, AccountAuthTokensMock)),
        // New recovery key validation
        Ok(AccountAuthenticator.AuthData(FullAccountIdMock.serverId, AccountAuthTokensMock))
      )

      val request = AuthKeyRotationRequest.Start(
        hwFactorProofOfPossession = HwFactorProofOfPossession("signed-token"),
        hwSignedAccountId = "signed-account-id",
        hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
        newKeys = generateAppAuthKeys
      )
      val result = fullAccountAuthKeyRotationService.startOrResumeAuthKeyRotation(
        request = request,
        account = FullAccountMock
      )
      result.shouldBeErrOfType<Unexpected>()
        .retryRequest
        .shouldBeTypeOf<AuthKeyRotationRequest.Resume>()

      authKeyRotationAttemptDao.getAuthKeyRotationAttemptStateCalls.expectNoEvents()
      authKeyRotationAttemptDao.setAuthKeysWrittenCalls.awaitItem()
      // Note: clear is never called on authKeyRotationAttemptDao
      rotateAuthKeysF8eClient.rotateKeysetCalls.awaitItem()
      keyboxDao.rotateAuthKeysCalls.awaitItem()
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedGlobalAuthKey
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedRecoveryAuthKey
      accountAuthenticator.authResults.shouldBeEmpty()
      relationshipsService.syncCalls.awaitItem()
      fullAccountCloudBackupCreator.createCalls.awaitItem()
    }
  }

  test("rotation response doesn't matter") {
    checkAll(rotateAuthKeysSuccessResponses + rotateAuthKeysFailedResponses) { rotateAuthKeysResponse ->
      cloudBackupDao.set(
        FullAccountMock.accountId.serverId,
        CloudBackupV2WithFullAccountMock
      )
      cloudStoreAccountRepository.currentAccountResult = Ok(cloudAccount)
      fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)
      rotateAuthKeysF8eClient.rotateKeysetResult = rotateAuthKeysResponse

      accountAuthenticator.authResults = mutableListOf(
        // New global key validation
        authSuccessResponse,
        // New recovery key validation
        authSuccessResponse
      )

      val request = AuthKeyRotationRequest.Start(
        hwFactorProofOfPossession = HwFactorProofOfPossession("signed-token"),
        hwSignedAccountId = "signed-account-id",
        hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
        newKeys = generateAppAuthKeys
      )
      val result = fullAccountAuthKeyRotationService.startOrResumeAuthKeyRotation(
        request = request,
        account = FullAccountMock
      )
      result.shouldBeOk()

      authKeyRotationAttemptDao.setAuthKeysWrittenCalls.awaitItem()
      authKeyRotationAttemptDao.clearCalls.awaitItem()
      rotateAuthKeysF8eClient.rotateKeysetCalls.awaitItem()
      keyboxDao.rotateAuthKeysCalls.awaitItem()
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedGlobalAuthKey
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedRecoveryAuthKey
      accountAuthenticator.authResults.shouldBeEmpty()
      fullAccountCloudBackupCreator.createCalls.awaitItem()

      relationshipsService.syncCalls.awaitItem()
    }
  }

  test("new global key is invalid, old key still works") {
    authFailedResponses.checkAll { newGlobalKeyResponse ->
      accountAuthenticator.authResults = mutableListOf(
        // New global key validation
        newGlobalKeyResponse,
        // Old global key validation
        authSuccessResponse,
        // Old recovery key validation
        authSuccessResponse
      )

      val request = AuthKeyRotationRequest.Start(
        hwFactorProofOfPossession = HwFactorProofOfPossession("signed-token"),
        hwSignedAccountId = "signed-account-id",
        hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
        newKeys = generateAppAuthKeys
      )
      val result = fullAccountAuthKeyRotationService.startOrResumeAuthKeyRotation(
        request = request,
        account = FullAccountMock
      )
      result.shouldBeErrOfType<AuthKeyRotationFailure.Acceptable>()

      authKeyRotationAttemptDao.setAuthKeysWrittenCalls.awaitItem()
      authKeyRotationAttemptDao.clearCalls.awaitItem()
      rotateAuthKeysF8eClient.rotateKeysetCalls.awaitItem()
      keyboxDao.rotateAuthKeysCalls.expectNoEvents()
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedGlobalAuthKey
      accountAuthenticator.authCalls.awaitItem() shouldBe KeyboxMock.activeAppKeyBundle.authKey
      accountAuthenticator.authCalls.awaitItem() shouldBe KeyboxMock.activeAppKeyBundle.recoveryAuthKey
      accountAuthenticator.authResults.shouldBeEmpty()
    }
  }

  test("new global key is valid, new recovery key is invalid, old key still works") {
    authFailedResponses.checkAll { newRecoveryKeyResponse ->
      accountAuthenticator.authResults = mutableListOf(
        // New global key validation
        authSuccessResponse,
        // New recovery key validation
        newRecoveryKeyResponse,
        // Old global key validation
        authSuccessResponse,
        // Old recovery key validation
        authSuccessResponse
      )

      val request = AuthKeyRotationRequest.Start(
        hwFactorProofOfPossession = HwFactorProofOfPossession("signed-token"),
        hwSignedAccountId = "signed-account-id",
        hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
        newKeys = generateAppAuthKeys
      )
      val result = fullAccountAuthKeyRotationService.startOrResumeAuthKeyRotation(
        request = request,
        account = FullAccountMock
      )
      result.shouldBeErrOfType<AuthKeyRotationFailure.Acceptable>()

      authKeyRotationAttemptDao.setAuthKeysWrittenCalls.awaitItem()
      authKeyRotationAttemptDao.clearCalls.awaitItem()
      rotateAuthKeysF8eClient.rotateKeysetCalls.awaitItem()
      keyboxDao.rotateAuthKeysCalls.expectNoEvents()
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedGlobalAuthKey
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedRecoveryAuthKey
      accountAuthenticator.authCalls.awaitItem() shouldBe KeyboxMock.activeAppKeyBundle.authKey
      accountAuthenticator.authCalls.awaitItem() shouldBe KeyboxMock.activeAppKeyBundle.recoveryAuthKey
      accountAuthenticator.authResults.shouldBeEmpty()
    }
  }

  test("new global key is invalid, old global key is invalid") {
    checkAll(
      // New global key validation responses
      authFailedResponses,
      // Old global key validation responses
      authFailedResponses
    ) { newGlobalKeyResponse, oldGlobalKeyResponse ->
      accountAuthenticator.authResults = mutableListOf(
        // New global key validation
        newGlobalKeyResponse,
        // Old global key validation
        oldGlobalKeyResponse
      )

      val request = AuthKeyRotationRequest.Start(
        hwFactorProofOfPossession = HwFactorProofOfPossession("signed-token"),
        hwSignedAccountId = "signed-account-id",
        hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
        newKeys = generateAppAuthKeys
      )
      val result = fullAccountAuthKeyRotationService.startOrResumeAuthKeyRotation(
        request = request,
        account = FullAccountMock
      )
      result.shouldBeErrOfType<AuthKeyRotationFailure.AccountLocked>()

      authKeyRotationAttemptDao.setAuthKeysWrittenCalls.awaitItem()
      authKeyRotationAttemptDao.clearCalls.expectNoEvents()
      rotateAuthKeysF8eClient.rotateKeysetCalls.awaitItem()
      keyboxDao.rotateAuthKeysCalls.expectNoEvents()
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedGlobalAuthKey
      accountAuthenticator.authCalls.awaitItem() shouldBe KeyboxMock.activeAppKeyBundle.authKey
      accountAuthenticator.authResults.shouldBeEmpty()
    }
  }

  test("new recovery key is invalid, old recovery key is invalid") {
    checkAll(
      // New recovery key validation responses
      authFailedResponses,
      // Old recovery key validation responses
      authFailedResponses
    ) { newRecoveryKeyResponse, oldRecoveryKeyResponse ->
      accountAuthenticator.authResults = mutableListOf(
        // New global key validation
        authSuccessResponse,
        // New recovery key validation
        newRecoveryKeyResponse,
        // Old global key validation
        authSuccessResponse,
        // Old recovery key validation
        oldRecoveryKeyResponse
      )

      val request = AuthKeyRotationRequest.Start(
        hwFactorProofOfPossession = HwFactorProofOfPossession("signed-token"),
        hwSignedAccountId = "signed-account-id",
        hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
        newKeys = generateAppAuthKeys
      )
      val result = fullAccountAuthKeyRotationService.startOrResumeAuthKeyRotation(
        request = request,
        account = FullAccountMock
      )
      result.shouldBeErrOfType<AuthKeyRotationFailure.AccountLocked>()

      authKeyRotationAttemptDao.setAuthKeysWrittenCalls.awaitItem()
      authKeyRotationAttemptDao.clearCalls.expectNoEvents()
      rotateAuthKeysF8eClient.rotateKeysetCalls.awaitItem()
      keyboxDao.rotateAuthKeysCalls.expectNoEvents()
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedGlobalAuthKey
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedRecoveryAuthKey
      accountAuthenticator.authCalls.awaitItem() shouldBe KeyboxMock.activeAppKeyBundle.authKey
      accountAuthenticator.authCalls.awaitItem() shouldBe KeyboxMock.activeAppKeyBundle.recoveryAuthKey
      accountAuthenticator.authResults.shouldBeEmpty()
    }
  }

  test("new global key validation fails unexpectedly") {
    unexpectedAuthResponses.checkAll { newGlobalKeyResponse ->
      accountAuthenticator.authResults = mutableListOf(
        // New global key validation
        newGlobalKeyResponse
      )

      val request = AuthKeyRotationRequest.Start(
        hwFactorProofOfPossession = HwFactorProofOfPossession("signed-token"),
        hwSignedAccountId = "signed-account-id",
        hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
        newKeys = generateAppAuthKeys
      )
      val result = fullAccountAuthKeyRotationService.startOrResumeAuthKeyRotation(
        request = request,
        account = FullAccountMock
      )
      result.shouldBeErrOfType<Unexpected>()

      authKeyRotationAttemptDao.setAuthKeysWrittenCalls.awaitItem()
      authKeyRotationAttemptDao.clearCalls.expectNoEvents()
      rotateAuthKeysF8eClient.rotateKeysetCalls.awaitItem()
      keyboxDao.rotateAuthKeysCalls.expectNoEvents()
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedGlobalAuthKey
      accountAuthenticator.authCalls.expectNoEvents()
      accountAuthenticator.authResults.shouldBeEmpty()
    }
  }

  test("new global key valid, new recovery key validation fails unexpectedly") {
    unexpectedAuthResponses.checkAll { newRecoveryKeyResponse ->
      accountAuthenticator.authResults = mutableListOf(
        // New global key validation
        authSuccessResponse,
        // New recovery key validation
        newRecoveryKeyResponse
      )

      val request = AuthKeyRotationRequest.Start(
        hwFactorProofOfPossession = HwFactorProofOfPossession("signed-token"),
        hwSignedAccountId = "signed-account-id",
        hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
        newKeys = generateAppAuthKeys
      )
      val result = fullAccountAuthKeyRotationService.startOrResumeAuthKeyRotation(
        request = request,
        account = FullAccountMock
      )
      result.shouldBeErrOfType<Unexpected>()

      authKeyRotationAttemptDao.setAuthKeysWrittenCalls.awaitItem()
      authKeyRotationAttemptDao.clearCalls.expectNoEvents()
      rotateAuthKeysF8eClient.rotateKeysetCalls.awaitItem()
      keyboxDao.rotateAuthKeysCalls.expectNoEvents()
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedGlobalAuthKey
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedRecoveryAuthKey
      accountAuthenticator.authCalls.expectNoEvents()
      accountAuthenticator.authResults.shouldBeEmpty()
    }
  }

  test("new global key invalid, old global key fails unexpectedly") {
    checkAll(
      // New global key validation responses
      authFailedResponses,
      // Old global key validation responses
      unexpectedAuthResponses
    ) { newGlobalKeyResponse, oldGlobalKeyResponse ->
      accountAuthenticator.authResults = mutableListOf(
        // New global key validation
        newGlobalKeyResponse,
        // Old global key validation
        oldGlobalKeyResponse
      )

      val request = AuthKeyRotationRequest.Start(
        hwFactorProofOfPossession = HwFactorProofOfPossession("signed-token"),
        hwSignedAccountId = "signed-account-id",
        hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
        newKeys = generateAppAuthKeys
      )
      val result = fullAccountAuthKeyRotationService.startOrResumeAuthKeyRotation(
        request = request,
        account = FullAccountMock
      )
      result.shouldBeErrOfType<Unexpected>()

      authKeyRotationAttemptDao.setAuthKeysWrittenCalls.awaitItem()
      authKeyRotationAttemptDao.clearCalls.expectNoEvents()
      rotateAuthKeysF8eClient.rotateKeysetCalls.awaitItem()
      keyboxDao.rotateAuthKeysCalls.expectNoEvents()
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedGlobalAuthKey
      accountAuthenticator.authCalls.awaitItem() shouldBe KeyboxMock.activeAppKeyBundle.authKey
      accountAuthenticator.authResults.shouldBeEmpty()
    }
  }

  test("new recovery key invalid, old recovery key fails unexpectedly") {
    checkAll(
      // New recovery key validation responses
      authFailedResponses,
      // Old recovery key validation responses
      unexpectedAuthResponses
    ) { newRecoveryKeyResponse, oldRecoveryKeyResponse ->
      accountAuthenticator.authResults = mutableListOf(
        // New global key validation
        authSuccessResponse,
        // New recovery key validation
        newRecoveryKeyResponse,
        // Old global key validation
        authSuccessResponse,
        // Old recovery key validation
        oldRecoveryKeyResponse
      )

      val request = AuthKeyRotationRequest.Start(
        hwFactorProofOfPossession = HwFactorProofOfPossession("signed-token"),
        hwSignedAccountId = "signed-account-id",
        hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
        newKeys = generateAppAuthKeys
      )
      val result = fullAccountAuthKeyRotationService.startOrResumeAuthKeyRotation(
        request = request,
        account = FullAccountMock
      )
      result.shouldBeErrOfType<Unexpected>()

      authKeyRotationAttemptDao.setAuthKeysWrittenCalls.awaitItem()
      authKeyRotationAttemptDao.clearCalls.expectNoEvents()
      rotateAuthKeysF8eClient.rotateKeysetCalls.awaitItem()
      keyboxDao.rotateAuthKeysCalls.expectNoEvents()
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedGlobalAuthKey
      accountAuthenticator.authCalls.awaitItem() shouldBe generatedRecoveryAuthKey
      accountAuthenticator.authCalls.awaitItem() shouldBe KeyboxMock.activeAppKeyBundle.authKey
      accountAuthenticator.authCalls.awaitItem() shouldBe KeyboxMock.activeAppKeyBundle.recoveryAuthKey
      accountAuthenticator.authResults.shouldBeEmpty()
    }
  }
}) {
  companion object {
    val authSuccessResponse =
      Ok(AccountAuthenticator.AuthData(FullAccountIdMock.serverId, AccountAuthTokensMock))

    val authSuccessResponses = listOf(
      authSuccessResponse
    ).exhaustive()
    val authFailedResponses = listOf(
      AuthSignatureMismatch,
      AuthProtocolError(message = "protocol error")
    ).map(::Err).exhaustive()

    val unexpectedAuthResponses = listOf(
      AccountMissing,
      AuthStorageError(message = "storage error"),
      AuthNetworkError(message = "network error"),
      FailedToReadAccountStatus(cause = Error("failed ot read account status")),
      FailedToReadRecoveryStatus(cause = Error("failed to read recovery status")),
      AppRecoveryAuthPublicKeyMissing,
      RequestGlobalScopeForLiteAccount,
      UnhandledError(cause = RuntimeException("totally unexpected error"))
    ).map(::Err).exhaustive()

    val rotateAuthKeysSuccessResponse = Ok(Unit)
    val rotateAuthKeysSuccessResponses = listOf(rotateAuthKeysSuccessResponse).exhaustive()
    val rotateAuthKeysFailedResponses = listOf(
      HttpStatusCode.allStatusCodes.filter {
        it.isClientError
      }.map { HttpError.ClientError(HttpResponseMock(it)) },
      HttpStatusCode.allStatusCodes.filter {
        it.isServerError
      }.map { HttpError.ServerError(HttpResponseMock(it)) },
      listOf(
        HttpError.NetworkError(cause = RuntimeException("network error")),
        HttpError.UnhandledError(response = HttpResponseMock(HttpStatusCode.TooEarly)),
        HttpError.UnhandledException(cause = RuntimeException("unexpected exception")),
        HttpBodyError.DoubleReceiveError(cause = RuntimeException("double receive error")),
        HttpBodyError.SerializationError(cause = RuntimeException("serialization error")),
        HttpBodyError.UnhandledError(cause = RuntimeException("unexpected error"))
      )
    ).flatten().map(::Err).exhaustive()
  }
}
