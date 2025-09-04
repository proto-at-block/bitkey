package build.wallet.recovery

import bitkey.account.AccountConfigServiceFake
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.SpecificClientErrorMock
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode.NO_RECOVERY_EXISTS
import build.wallet.auth.AccountAuthenticatorMock
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitkey.auth.HwAuthPublicKeyMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.db.DbQueryError
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.auth.AuthF8eClientMock
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClientMock
import build.wallet.f8e.recovery.InitiateAccountDelayNotifyF8eClientFake
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.f8e.recovery.ListKeysetsF8eClientMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.EncryptedDescriptorBackupsFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.ktor.result.HttpError.ServerError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.notifications.DeviceTokenManagerMock
import build.wallet.recovery.CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError
import build.wallet.recovery.CancelDelayNotifyRecoveryError.LocalCancelDelayNotifyError
import build.wallet.recovery.LostAppAndCloudRecoveryService.CompletedAuth
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import build.wallet.testing.shouldBeOkOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import okio.ByteString.Companion.encodeUtf8

class LostAppAndCloudRecoveryServiceImplTests : FunSpec({

  val cancelDelayNotifyRecoveryF8eClient = CancelDelayNotifyRecoveryF8eClientMock(turbines::create)
  val authF8eClient = AuthF8eClientMock()
  val recoveryStatusService = RecoveryStatusServiceMock(
    StillRecoveringInitiatedRecoveryMock,
    turbines::create
  )
  val accountAuthenticator = AccountAuthenticatorMock(turbines::create)
  val authTokensService = AuthTokensServiceFake()
  val deviceTokenManager = DeviceTokenManagerMock(turbines::create)
  val appKeysGenerator = AppKeysGeneratorMock()
  val listKeysetsF8eClient = ListKeysetsF8eClientMock()
  val accountConfigService = AccountConfigServiceFake()
  val initiateAccountDelayNotifyF8eClient = InitiateAccountDelayNotifyF8eClientFake()
  val sqlDriver = inMemorySqlDriver()
  val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
  val recoveryDao = RecoveryDaoImpl(databaseProvider)
  val useEncryptedDescriptorBackupsFeatureFlag = EncryptedDescriptorBackupsFeatureFlag(
    FeatureFlagDaoFake()
  )
  val service = LostAppAndCloudRecoveryServiceImpl(
    authF8eClient = authF8eClient,
    cancelDelayNotifyRecoveryF8eClient = cancelDelayNotifyRecoveryF8eClient,
    recoveryStatusService = recoveryStatusService,
    recoveryLock = RecoveryLockImpl(),
    accountConfigService = accountConfigService,
    accountAuthenticator = accountAuthenticator,
    authTokensService = authTokensService,
    deviceTokenManager = deviceTokenManager,
    appKeysGenerator = appKeysGenerator,
    listKeysetsF8eClient = listKeysetsF8eClient,
    initiateAccountDelayNotifyF8eClient = initiateAccountDelayNotifyF8eClient,
    recoveryDao = recoveryDao,
    useEncryptedDescriptorBackupsFeatureFlag = useEncryptedDescriptorBackupsFeatureFlag
  )

  suspend fun LostAppAndCloudRecoveryService.cancel() =
    cancelRecovery(
      accountId = FullAccountIdMock,
      hwProofOfPossession = HwFactorProofOfPossession("")
    )

  beforeTest {
    cancelDelayNotifyRecoveryF8eClient.reset()
    recoveryStatusService.reset()
    accountConfigService.reset()
    authF8eClient.reset()
    accountAuthenticator.reset()
    authTokensService.reset()
    deviceTokenManager.reset()
    listKeysetsF8eClient.reset()
    appKeysGenerator.reset()
    recoveryDao.clear()
    useEncryptedDescriptorBackupsFeatureFlag.reset()

    useEncryptedDescriptorBackupsFeatureFlag.setFlagValue(true)
  }

  test("success") {
    service.cancel().shouldBeOkOfType<Unit>()

    recoveryStatusService.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("success - ignore general 400") {
    cancelDelayNotifyRecoveryF8eClient.cancelResult =
      Err(SpecificClientErrorMock(NO_RECOVERY_EXISTS))

    service.cancel().shouldBeOk()

    recoveryStatusService.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("failure - backend") {
    cancelDelayNotifyRecoveryF8eClient.cancelResult =
      Err(F8eError.ServerError(ServerError(HttpResponseMock(InternalServerError))))

    service.cancel().shouldBeErrOfType<F8eCancelDelayNotifyError>()

    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("failure - dao") {
    recoveryStatusService.clearCallResult = Err(DbQueryError(IllegalStateException()))

    service.cancel().shouldBeErrOfType<LocalCancelDelayNotifyError>()

    recoveryStatusService.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("completeAuth returns WithDirectKeys when no descriptor backups available") {
    val hwAuthKey = HwAuthPublicKeyMock
    val accountId = FullAccountIdMock
    val session = "test-session"
    val hwSignedChallenge = "test-challenge"

    listKeysetsF8eClient.result = Ok(
      ListKeysetsF8eClient.ListKeysetsResponse(
        keysets = listOf(SpendingKeysetMock),
        wrappedSsek = null,
        descriptorBackups = null
      )
    )

    val result = service.completeAuth(accountId, session, hwAuthKey, hwSignedChallenge)
    result.shouldBeOk()

    val completedAuth = result.value
    completedAuth shouldBe instanceOf<CompletedAuth.WithDirectKeys>()

    val directKeys = completedAuth as CompletedAuth.WithDirectKeys
    directKeys.existingHwSpendingKeys.size shouldBe 1
    directKeys.existingHwSpendingKeys.first() shouldBe SpendingKeysetMock.hardwareKey

    // Consume the device token event
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
  }

  test("completeAuth returns WithDescriptorBackups when backups are up to date") {
    val hwAuthKey = HwAuthPublicKeyMock
    val accountId = FullAccountIdMock
    val session = "test-session"
    val hwSignedChallenge = "test-challenge"

    val mockDescriptorBackups = listOf(
      bitkey.backup.DescriptorBackup(
        keysetId = "keyset-1",
        sealedDescriptor = XCiphertext(value = "encrypted-descriptor")
      )
    )
    val mockWrappedSsek = "wrapped-ssek".encodeUtf8()

    listKeysetsF8eClient.result = Ok(
      ListKeysetsF8eClient.ListKeysetsResponse(
        keysets = listOf(SpendingKeysetMock),
        wrappedSsek = mockWrappedSsek,
        descriptorBackups = mockDescriptorBackups
      )
    )

    val result = service.completeAuth(accountId, session, hwAuthKey, hwSignedChallenge)
    result.shouldBeOk()

    val completedAuth = result.value
    completedAuth shouldBe instanceOf<CompletedAuth.WithDescriptorBackups>()

    val withBackups = completedAuth as CompletedAuth.WithDescriptorBackups
    withBackups.descriptorBackups shouldBe mockDescriptorBackups
    withBackups.wrappedSsek shouldBe mockWrappedSsek

    // Consume the device token event
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
  }

  test("completeAuth returns WithDirectKeys when backups count doesn't match keysets") {
    val hwAuthKey = HwAuthPublicKeyMock
    val accountId = FullAccountIdMock
    val session = "test-session"
    val hwSignedChallenge = "test-challenge"

    val mockDescriptorBackups = listOf(
      bitkey.backup.DescriptorBackup(
        keysetId = "keyset-1",
        sealedDescriptor = XCiphertext(value = "encrypted-descriptor")
      )
    )
    val mockWrappedSsek = "wrapped-ssek".encodeUtf8()

    listKeysetsF8eClient.result = Ok(
      ListKeysetsF8eClient.ListKeysetsResponse(
        keysets = listOf(SpendingKeysetMock, SpendingKeysetMock),
        wrappedSsek = mockWrappedSsek,
        descriptorBackups = mockDescriptorBackups
      )
    )

    val result = service.completeAuth(accountId, session, hwAuthKey, hwSignedChallenge)
    result.shouldBeOk()

    val completedAuth = result.value
    completedAuth shouldBe instanceOf<CompletedAuth.WithDirectKeys>()

    val directKeys = completedAuth as CompletedAuth.WithDirectKeys
    directKeys.existingHwSpendingKeys.size shouldBe 2
    directKeys.existingHwSpendingKeys.first() shouldBe SpendingKeysetMock.hardwareKey

    // Consume the device token event
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
  }

  test("completeAuth returns WithDirectKeys when wrappedSsek is null") {
    val hwAuthKey = HwAuthPublicKeyMock
    val accountId = FullAccountIdMock
    val session = "test-session"
    val hwSignedChallenge = "test-challenge"

    val mockDescriptorBackups = listOf(
      bitkey.backup.DescriptorBackup(
        keysetId = "keyset-1",
        sealedDescriptor = XCiphertext(value = "encrypted-descriptor")
      )
    )

    listKeysetsF8eClient.result = Ok(
      ListKeysetsF8eClient.ListKeysetsResponse(
        keysets = listOf(SpendingKeysetMock),
        wrappedSsek = null,
        descriptorBackups = mockDescriptorBackups
      )
    )

    val result = service.completeAuth(accountId, session, hwAuthKey, hwSignedChallenge)
    result.shouldBeOk()

    val completedAuth = result.value
    completedAuth shouldBe instanceOf<CompletedAuth.WithDirectKeys>()

    val directKeys = completedAuth as CompletedAuth.WithDirectKeys
    directKeys.existingHwSpendingKeys.size shouldBe 1
    directKeys.existingHwSpendingKeys.first() shouldBe SpendingKeysetMock.hardwareKey

    // Consume the device token event
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
  }

  test("completeAuth returns WithDescriptorBackups when no keysets but backups exist") {
    val hwAuthKey = HwAuthPublicKeyMock
    val accountId = FullAccountIdMock
    val session = "test-session"
    val hwSignedChallenge = "test-challenge"

    val mockDescriptorBackups = listOf(
      bitkey.backup.DescriptorBackup(
        keysetId = "keyset-1",
        sealedDescriptor = XCiphertext(value = "encrypted-descriptor")
      )
    )
    val mockWrappedSsek = "wrapped-ssek".encodeUtf8()

    listKeysetsF8eClient.result = Ok(
      ListKeysetsF8eClient.ListKeysetsResponse(
        keysets = emptyList(),
        wrappedSsek = mockWrappedSsek,
        descriptorBackups = mockDescriptorBackups
      )
    )

    val result = service.completeAuth(accountId, session, hwAuthKey, hwSignedChallenge)
    result.shouldBeOk()

    val completedAuth = result.value
    completedAuth shouldBe instanceOf<CompletedAuth.WithDescriptorBackups>()

    val withBackups = completedAuth as CompletedAuth.WithDescriptorBackups
    withBackups.descriptorBackups shouldBe mockDescriptorBackups
    withBackups.wrappedSsek shouldBe mockWrappedSsek

    // Consume the device token event
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
  }
})
