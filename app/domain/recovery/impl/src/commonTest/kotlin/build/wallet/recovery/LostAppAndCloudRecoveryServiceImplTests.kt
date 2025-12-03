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
import build.wallet.db.DbQueryError
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.auth.AuthF8eClientMock
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.*
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.EncryptedDescriptorBackupsFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.ktor.result.HttpError.ServerError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.notifications.DeviceTokenManagerMock
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.recovery.CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError
import build.wallet.recovery.CancelDelayNotifyRecoveryError.LocalCancelDelayNotifyError
import build.wallet.recovery.LostAppAndCloudRecoveryService.CompletedAuth
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
  val accountAuthenticator = AccountAuthenticatorMock(turbines::create)
  val authTokensService = AuthTokensServiceFake()
  val deviceTokenManager = DeviceTokenManagerMock(turbines::create)
  val appKeysGenerator = AppKeysGeneratorMock()
  val listKeysetsF8eClient = ListKeysetsF8eClientMock()
  val accountConfigService = AccountConfigServiceFake()
  val initiateAccountDelayNotifyF8eClient = InitiateAccountDelayNotifyF8eClientFake()
  val recoveryDao = RecoveryDaoMock(turbines::create)
  val useEncryptedDescriptorBackupsFeatureFlag = EncryptedDescriptorBackupsFeatureFlag(
    FeatureFlagDaoFake()
  )
  val uuidGenerator = UuidGeneratorFake()
  val mockRemoteKeyset =
    LegacyRemoteKeyset(
      keysetId = SpendingKeysetMock.f8eSpendingKeyset.keysetId,
      networkType = SpendingKeysetMock.networkType.name,
      appDescriptor = SpendingKeysetMock.appKey.key.dpub,
      hardwareDescriptor = SpendingKeysetMock.hardwareKey.key.dpub,
      serverDescriptor = SpendingKeysetMock.f8eSpendingKeyset.spendingPublicKey.key.dpub
    )
  val service = LostAppAndCloudRecoveryServiceImpl(
    authF8eClient = authF8eClient,
    cancelDelayNotifyRecoveryF8eClient = cancelDelayNotifyRecoveryF8eClient,
    recoveryLock = RecoveryLockImpl(),
    accountConfigService = accountConfigService,
    accountAuthenticator = accountAuthenticator,
    authTokensService = authTokensService,
    deviceTokenManager = deviceTokenManager,
    appKeysGenerator = appKeysGenerator,
    listKeysetsF8eClient = listKeysetsF8eClient,
    initiateAccountDelayNotifyF8eClient = initiateAccountDelayNotifyF8eClient,
    recoveryDao = recoveryDao,
    useEncryptedDescriptorBackupsFeatureFlag = useEncryptedDescriptorBackupsFeatureFlag,
    uuidGenerator = uuidGenerator
  )

  suspend fun LostAppAndCloudRecoveryService.cancel() =
    cancelRecovery(
      accountId = FullAccountIdMock,
      hwProofOfPossession = HwFactorProofOfPossession("")
    )

  beforeTest {
    cancelDelayNotifyRecoveryF8eClient.reset()
    accountConfigService.reset()
    authF8eClient.reset()
    accountAuthenticator.reset()
    authTokensService.reset()
    deviceTokenManager.reset()
    listKeysetsF8eClient.reset()
    appKeysGenerator.reset()
    recoveryDao.reset()
    useEncryptedDescriptorBackupsFeatureFlag.reset()

    useEncryptedDescriptorBackupsFeatureFlag.setFlagValue(true)
  }

  test("success") {
    service.cancel().shouldBeOkOfType<Unit>()

    recoveryDao.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("success - ignore general 400") {
    cancelDelayNotifyRecoveryF8eClient.cancelResult =
      Err(SpecificClientErrorMock(NO_RECOVERY_EXISTS))

    service.cancel().shouldBeOk()

    recoveryDao.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("failure - backend") {
    cancelDelayNotifyRecoveryF8eClient.cancelResult =
      Err(F8eError.ServerError(ServerError(HttpResponseMock(InternalServerError))))

    service.cancel().shouldBeErrOfType<F8eCancelDelayNotifyError>()

    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("failure - dao") {
    recoveryDao.clearCallResult = Err(DbQueryError(IllegalStateException()))

    service.cancel().shouldBeErrOfType<LocalCancelDelayNotifyError>()

    recoveryDao.clearCalls.awaitItem()
    cancelDelayNotifyRecoveryF8eClient.cancelRecoveryCalls.awaitItem()
  }

  test("completeAuth returns WithDirectKeys when no descriptor backups available") {
    val hwAuthKey = HwAuthPublicKeyMock
    val accountId = FullAccountIdMock
    val session = "test-session"
    val hwSignedChallenge = "test-challenge"

    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = listOf(mockRemoteKeyset),
        wrappedSsek = null,
        descriptorBackups = emptyList()
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

  context("descriptor backups matrix for private wallet root xpub") {
    listOf(null, "foo").forEach { privateWalletRootXpub ->
      val matrixLabel = privateWalletRootXpub ?: "null"

      test("completeAuth returns WithDescriptorBackups when backups exist [$matrixLabel]") {
        val hwAuthKey = HwAuthPublicKeyMock
        val accountId = FullAccountIdMock
        val session = "test-session"
        val hwSignedChallenge = "test-challenge"

        val mockDescriptorBackups = listOf(
          bitkey.backup.DescriptorBackup(
            keysetId = "keyset-1",
            sealedDescriptor = XCiphertext(value = "encrypted-descriptor"),
            privateWalletRootXpub = privateWalletRootXpub?.let { XCiphertext("sealed-$it") }
          )
        )
        val mockWrappedSsek = "wrapped-ssek".encodeUtf8()

        listKeysetsF8eClient.result = Ok(
          ListKeysetsResponse(
            keysets = listOf(mockRemoteKeyset),
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
    }
  }
})
