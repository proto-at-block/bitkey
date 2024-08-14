package build.wallet.auth

import build.wallet.auth.AuthKeyRotationFailure.Unexpected
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.cloud.backup.BestEffortFullAccountCloudBackupUploader.Failure.BreakingError
import build.wallet.cloud.backup.BestEffortFullAccountCloudBackupUploader.Failure.IgnorableError
import build.wallet.cloud.backup.BestEffortFullAccountCloudBackupUploaderMock
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
import build.wallet.recovery.socrec.SocRecRelationshipsRepositoryMock
import build.wallet.recovery.socrec.TrustedContactKeyAuthenticatorMock
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.exhaustive
import io.kotest.property.exhaustive.plus
import io.ktor.http.HttpStatusCode

class FullAccountAuthKeyRotationServiceImplTests : FunSpec({

  val authKeyRotationAttemptDao = AuthKeyRotationAttemptDaoMock(turbines::create)
  val rotateAuthKeysF8eClient = RotateAuthKeysF8eClientMock(turbines::create)
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val accountAuthenticator = AccountAuthenticatorMock(turbines::create)
  val bestEffortFullAccountCloudBackupUploader =
    BestEffortFullAccountCloudBackupUploaderMock(turbines::create)
  val socRecRelationshipsRepository = SocRecRelationshipsRepositoryMock(turbines::create)
  val trustedContactKeyAuthenticator = TrustedContactKeyAuthenticatorMock(turbines::create)

  val fullAccountAuthKeyRotationService = FullAccountAuthKeyRotationServiceImpl(
    authKeyRotationAttemptDao = authKeyRotationAttemptDao,
    rotateAuthKeysF8eClient = rotateAuthKeysF8eClient,
    keyboxDao = keyboxDao,
    accountAuthenticator = accountAuthenticator,
    bestEffortFullAccountCloudBackupUploader = bestEffortFullAccountCloudBackupUploader,
    socRecRelationshipsRepository = socRecRelationshipsRepository,
    trustedContactKeyAuthenticator = trustedContactKeyAuthenticator
  )

  beforeEach {
    keyboxDao.reset()
    bestEffortFullAccountCloudBackupUploader.reset()
    rotateAuthKeysF8eClient.reset()
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
    bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackupCalls.awaitItem()
      .shouldBeTypeOf<FullAccount>()
      .should { backedUpAccount ->
        backedUpAccount.keybox.activeAppKeyBundle.authKey shouldBe generatedGlobalAuthKey
        backedUpAccount.keybox.activeAppKeyBundle.recoveryAuthKey shouldBe generatedRecoveryAuthKey
        backedUpAccount.keybox.appGlobalAuthKeyHwSignature shouldBe generatedGlobalAuthKeyHwSignature
      }

    socRecRelationshipsRepository.syncCalls.awaitItem()
  }

  test("resume auth key rotation") {
    accountAuthenticator.authResults = mutableListOf(
      // New global key validation
      Ok(AccountAuthenticator.AuthData(FullAccountIdMock.serverId, AccountAuthTokensMock)),
      // New recovery key validation
      Ok(AccountAuthenticator.AuthData(FullAccountIdMock.serverId, AccountAuthTokensMock))
    )

    val request = AuthKeyRotationRequest.Resume(newKeys = generateAppAuthKeys)
    keyboxDao.rotateKeyboxResult = Ok(
      KeyboxMock.copy(
        activeAppKeyBundle = KeyboxMock.activeAppKeyBundle.copy(
          authKey = request.newKeys.appGlobalAuthPublicKey,
          recoveryAuthKey = request.newKeys.appRecoveryAuthPublicKey
        ),
        appGlobalAuthKeyHwSignature = request.newKeys.appGlobalAuthKeyHwSignature
      )
    )

    val result = fullAccountAuthKeyRotationService.startOrResumeAuthKeyRotation(
      request = request,
      account = FullAccountMock
    )
    result.shouldBeOk()

    authKeyRotationAttemptDao.setAuthKeysWrittenCalls.expectNoEvents()
    authKeyRotationAttemptDao.clearCalls.awaitItem()
    keyboxDao.rotateAuthKeysCalls.awaitItem()
    bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackupCalls.awaitItem()
      .shouldBeTypeOf<FullAccount>()
      .should { backedUpAccount ->
        backedUpAccount.keybox.activeAppKeyBundle.authKey shouldBe request.newKeys.appGlobalAuthPublicKey
        backedUpAccount.keybox.activeAppKeyBundle.recoveryAuthKey shouldBe request.newKeys.appRecoveryAuthPublicKey
        backedUpAccount.keybox.appGlobalAuthKeyHwSignature shouldBe request.newKeys.appGlobalAuthKeyHwSignature
      }

    accountAuthenticator.authCalls.awaitItem() shouldBe request.newKeys.appGlobalAuthPublicKey
    accountAuthenticator.authCalls.awaitItem() shouldBe request.newKeys.appRecoveryAuthPublicKey
    accountAuthenticator.authResults.shouldBeEmpty()

    socRecRelationshipsRepository.syncCalls.awaitItem()
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

    test("ignore ignorable cloud backup failure") {
      bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackupResult =
        Err(IgnorableError("who cares?"))

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
      bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackupCalls.awaitItem()
        .shouldBeTypeOf<FullAccount>()
        .should { backedUpAccount ->
          backedUpAccount.keybox.activeAppKeyBundle.authKey shouldBe generatedGlobalAuthKey
          backedUpAccount.keybox.activeAppKeyBundle.recoveryAuthKey shouldBe generatedRecoveryAuthKey
          backedUpAccount.keybox.appGlobalAuthKeyHwSignature shouldBe generatedGlobalAuthKeyHwSignature
        }

      socRecRelationshipsRepository.syncCalls.awaitItem()
    }

    test("handle breaking cloud backup error") {
      bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackupResult =
        Err(BreakingError("oh no!"))

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
      socRecRelationshipsRepository.syncCalls.awaitItem()
      bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackupCalls.awaitItem()
        .shouldBeTypeOf<FullAccount>()
        .should { backedUpAccount ->
          backedUpAccount.keybox.activeAppKeyBundle.authKey shouldBe generatedGlobalAuthKey
          backedUpAccount.keybox.activeAppKeyBundle.recoveryAuthKey shouldBe generatedRecoveryAuthKey
          backedUpAccount.keybox.appGlobalAuthKeyHwSignature shouldBe generatedGlobalAuthKeyHwSignature
        }
    }

    test("rotation response doesn't matter") {
      checkAll(rotateAuthKeysSuccessResponses + rotateAuthKeysFailedResponses) { rotateAuthKeysResponse ->
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
        bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackupCalls.awaitItem()
          .shouldBeTypeOf<FullAccount>()
          .should { backedUpAccount ->
            backedUpAccount.keybox.activeAppKeyBundle.authKey shouldBe generatedGlobalAuthKey
            backedUpAccount.keybox.activeAppKeyBundle.recoveryAuthKey shouldBe generatedRecoveryAuthKey
            backedUpAccount.keybox.appGlobalAuthKeyHwSignature shouldBe generatedGlobalAuthKeyHwSignature
          }

        socRecRelationshipsRepository.syncCalls.awaitItem()
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
        bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackupCalls.expectNoEvents()
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
        bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackupCalls.expectNoEvents()
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
        bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackupCalls.expectNoEvents()
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
        bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackupCalls.expectNoEvents()
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
        bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackupCalls.expectNoEvents()
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
        bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackupCalls.expectNoEvents()
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
        bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackupCalls.expectNoEvents()
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
        bestEffortFullAccountCloudBackupUploader.createAndUploadCloudBackupCalls.expectNoEvents()
      }
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
      FailedToReadAccountStatus(dbError = Error("failed ot read account status")),
      FailedToReadRecoveryStatus(dbError = Error("failed to read recovery status")),
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
