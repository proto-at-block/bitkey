package build.wallet.wallet.migration

import bitkey.backup.DescriptorBackup
import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope.Global
import bitkey.auth.AuthTokenScope.Recovery
import bitkey.auth.RefreshToken
import build.wallet.account.AccountServiceFake
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AccountAuthenticatorMock
import build.wallet.auth.AuthTokensServiceFake
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.auth.AppAuthPublicKeysMock
import build.wallet.bitkey.auth.AppRecoveryAuthPublicKeyMock2
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.f8e.F8eSpendingKeysetMock
import build.wallet.bitkey.f8e.F8eSpendingKeysetPrivateWalletMock
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.FullAccountW3Mock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.bitkey.keybox.PrivateWalletKeyboxMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.cloud.backup.csek.SsekDaoFake
import build.wallet.cloud.backup.csek.SsekFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.ActionProofHeader
import build.wallet.f8e.auth.AuthF8eClient
import build.wallet.f8e.auth.AuthF8eClientMock
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.auth.RotateAuthKeysF8eClientMock
import build.wallet.f8e.onboarding.CreateAccountKeysetV2F8eClientFake
import build.wallet.f8e.onboarding.SetActiveSpendingKeysetF8eClientFake
import build.wallet.f8e.recovery.ListKeysetsResponse
import build.wallet.f8e.recovery.ListKeysetsF8eClientMock
import build.wallet.f8e.recovery.SignedKeysetVerificationResponseMock
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.test.HttpResponseMock
import build.wallet.money.BitcoinMoney
import build.wallet.notifications.DeviceTokenManagerMock
import build.wallet.onboarding.OnboardingKeyboxSealedSsekDaoFake
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.recovery.DescriptorBackupServiceFake
import build.wallet.recovery.createFakeSpendingKeyset
import build.wallet.recovery.sweep.Sweep
import build.wallet.recovery.sweep.SweepPsbt
import build.wallet.recovery.sweep.SweepService
import build.wallet.recovery.sweep.SweepServiceMock
import build.wallet.recovery.sweep.SweepSignaturePlan
import build.wallet.relationships.DelegatedDecryptionKeyServiceMock
import build.wallet.relationships.EndorseTrustedContactsServiceMock
import build.wallet.relationships.RelationshipsServiceMock
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import build.wallet.testing.shouldBeOkOfType
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XCiphertext
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class MigrationServiceImplTests : FunSpec({
  val appKeysGenerator = AppKeysGeneratorMock()
  val createKeysetClient = CreateAccountKeysetV2F8eClientFake()
  val uuidGenerator = UuidGeneratorFake()
  val accountService = AccountServiceFake()
  val accountAuthenticator = AccountAuthenticatorMock(turbines::create)
  val authTokensService = AuthTokensServiceFake()
  val authF8eClient = AuthF8eClientMock(
    defaultInitiateAuthenticationResult = Err(HttpError.ClientError(HttpResponseMock(NotFound)))
  )
  val keyboxDao = KeyboxDaoMock(
    turbine = turbines::create
  )
  val privateWalletMigrationDao = PrivateWalletMigrationDaoFake()
  val w3UpgradeDao = W3UpgradeDaoFake()
  val w3UpgradeCheckpointWriter = W3UpgradeCheckpointWriterIntegrationFake(w3UpgradeDao, keyboxDao)
  val ssekDao = SsekDaoFake()
  val descriptorBackupService = DescriptorBackupServiceFake()
  val descriptorBackupVerificationDao = build.wallet.recovery.DescriptorBackupVerificationDaoFake()
  val listKeysetsClient = ListKeysetsF8eClientMock()
  val setActiveSpendingKeysetF8eClient = SetActiveSpendingKeysetF8eClientFake()
  val delegatedDecryptionKeyService = DelegatedDecryptionKeyServiceMock()
  val sweepService = SweepServiceMock()
  val rotateAuthKeysF8eClient = RotateAuthKeysF8eClientMock(turbines::create)
  val onboardingKeyboxSealedSsekDao = OnboardingKeyboxSealedSsekDaoFake()
  val relationshipsService = RelationshipsServiceMock(turbines::create, Clock.System)
  val endorseTrustedContactsService = EndorseTrustedContactsServiceMock(turbines::create)
  val deviceTokenManager = DeviceTokenManagerMock(turbines::create)

  val service = MigrationServiceImpl(
    appKeysGenerator = appKeysGenerator,
    authF8eClient = authF8eClient,
    createKeysetClient = createKeysetClient,
    uuidGenerator = uuidGenerator,
    accountService = accountService,
    accountAuthenticator = accountAuthenticator,
    authTokensService = authTokensService,
    keyboxDao = keyboxDao,
    privateWalletMigrationDao = privateWalletMigrationDao,
    w3UpgradeDao = w3UpgradeDao,
    w3UpgradeCheckpointWriter = w3UpgradeCheckpointWriter,
    ssekDao = ssekDao,
    onboardingKeyboxSealedSsekDao = onboardingKeyboxSealedSsekDao,
    descriptorBackupService = descriptorBackupService,
    listKeysetsF8eClient = listKeysetsClient,
    setActiveSpendingKeysetF8eClient = setActiveSpendingKeysetF8eClient,
    rotateAuthKeysF8eClient = rotateAuthKeysF8eClient,
    sweepService = sweepService,
    delegatedDecryptionKeyService = delegatedDecryptionKeyService,
    descriptorBackupVerificationDao = descriptorBackupVerificationDao,
    relationshipsService = relationshipsService,
    endorseTrustedContactsService = endorseTrustedContactsService,
    deviceTokenManager = deviceTokenManager
  )

  val mockAccount = FullAccountMock
  val mockProofOfPossession = HwFactorProofOfPossession("test-proof")
  val mockNewHwKeys = HwKeyBundleMock
  val mockSealedSsek = SealedSsekFake
  val descriptorBackupsProof = PrivilegedActionProof.HwSignedAction(
    actionProof = ActionProofHeader(signatures = listOf("descriptor-proof"))
  )
  val activateKeysetProof = PrivilegedActionProof.HwSignedAction(
    actionProof = ActionProofHeader(signatures = listOf("activate-proof"))
  )

  fun w3UpgradeKeyset() =
    SpendingKeysetMock.copy(
      localId = "uuid-0",
      appKey = AppKeyBundleMock.spendingKey,
      hardwareKey = mockNewHwKeys.spendingKey,
      f8eSpendingKeyset = F8eSpendingKeysetPrivateWalletMock.copy(keysetId = "new-f8e-spending-keyset-id")
    )

  fun w3RotatedAuthKeys(): AppAuthPublicKeys =
    AppAuthPublicKeys(
      appGlobalAuthPublicKey = mockAccount.keybox.activeAppKeyBundle.authKey,
      appRecoveryAuthPublicKey = AppRecoveryAuthPublicKeyMock2,
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("rotated-app-global-auth-hw-signature")
    )

  fun rotatedKeybox(
    appAuthKeys: AppAuthPublicKeys = w3RotatedAuthKeys(),
  ): Keybox = mockAccount.keybox.copy(
      activeAppKeyBundle = mockAccount.keybox.activeAppKeyBundle.copy(
        authKey = appAuthKeys.appGlobalAuthPublicKey,
        recoveryAuthKey = appAuthKeys.appRecoveryAuthPublicKey
      ),
      appGlobalAuthKeyHwSignature = appAuthKeys.appGlobalAuthKeyHwSignature
    )

  fun rotatedAccount(
    appAuthKeys: AppAuthPublicKeys = w3RotatedAuthKeys(),
  ): FullAccount = mockAccount.copy(keybox = rotatedKeybox(appAuthKeys))

  fun hwAuthInitiationSuccess() =
    AuthF8eClient.InitiateAuthenticationSuccess(
      username = "hardware-user",
      accountId = mockAccount.accountId.serverId,
      challenge = "hardware-challenge",
      session = "hardware-session"
    )

  fun authData(tokens: AccountAuthTokens) =
    AccountAuthenticator.AuthData(
      accountId = mockAccount.accountId.serverId,
      authTokens = tokens
    )

  beforeTest {
    appKeysGenerator.reset()
    createKeysetClient.reset()
    uuidGenerator.reset()
    accountService.reset()
    accountAuthenticator.reset()
    accountAuthenticator.authResults = mutableListOf(
      accountAuthenticator.defaultAuthResult,
      accountAuthenticator.defaultAuthResult
    )
    authTokensService.reset()
    authF8eClient.reset()
    keyboxDao.reset()
    privateWalletMigrationDao.clear()
    w3UpgradeDao.clear()
    w3UpgradeCheckpointWriter.reset()
    setActiveSpendingKeysetF8eClient.reset()
    listKeysetsClient.reset()
    ssekDao.reset()
    endorseTrustedContactsService.reset()
    descriptorBackupService.reset()
    rotateAuthKeysF8eClient.reset()
    onboardingKeyboxSealedSsekDao.reset()
  }

  test("resume returns NotStarted when no migration state exists") {
    accountService.setActiveAccount(mockAccount)

    val result = service.resume(MigrationType.PrivateWalletMigration)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.NotStarted>()
    state.type.shouldBe(MigrationType.PrivateWalletMigration)
  }

  test("resume returns Completed when account already has a private wallet") {
    accountService.setActiveAccount(
      FullAccountMock.copy(
        keybox = PrivateWalletKeyboxMock
      )
    )

    val result = service.resume(MigrationType.PrivateWalletMigration)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.Completed>()
  }

  test("resume still resumes interrupted migration even when local keybox is already private") {
    // Simulate an interrupted migration: the local keybox was already flipped to private
    // (handleCreateNewKeyset saves the new keyset as active), but cloud backup and sweep
    // are still incomplete in the DAO.
    accountService.setActiveAccount(
      FullAccountMock.copy(
        keybox = PrivateWalletKeyboxMock
      )
    )
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId("uuid-0")
    privateWalletMigrationDao.setDescriptorBackupComplete()
    privateWalletMigrationDao.setServerKeysetActive()
    // cloud backup and sweep NOT completed

    val result = service.resume(MigrationType.PrivateWalletMigration)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    // Should return CloudBackup so the migration can be resumed, NOT Completed
    state.shouldBeInstanceOf<MigrationProgress.CloudBackup>()
  }

  test("resume returns NotStarted when migration needs credentials (hardware key saved but keyset not complete)") {
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)

    val result = service.resume(MigrationType.PrivateWalletMigration)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    // Returns NotStarted because CreateNewKeyset requires credentials
    state.shouldBeInstanceOf<MigrationProgress.NotStarted>()
  }

  test("resume returns NotStarted when migration needs credentials (keyset complete but backup not done)") {
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId("uuid-0")

    val result = service.resume(MigrationType.PrivateWalletMigration)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    // Returns NotStarted because DescriptorBackup requires credentials
    state.shouldBeInstanceOf<MigrationProgress.NotStarted>()
  }

  test("resume returns Completed when migration is finished") {
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId("uuid-0")
    privateWalletMigrationDao.setDescriptorBackupComplete()
    privateWalletMigrationDao.setServerKeysetActive()
    privateWalletMigrationDao.setCloudBackupComplete()
    privateWalletMigrationDao.setSweepCompleted()

    val result = service.resume(MigrationType.PrivateWalletMigration)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.Completed>()
  }

  test("proceed from NotStarted returns error - must call start() first") {
    accountService.setActiveAccount(mockAccount)
    val startState = MigrationProgress.NotStarted(MigrationType.PrivateWalletMigration)

    val result = service.proceed(state = startState)

    result.shouldBeErrOfType<MigrationError.InvalidState>()
  }

  test("proceed from CreateNewKeyset creates new keyset and transitions to AuthKeyRotation") {
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    appKeysGenerator.keyBundleResult = Ok(AppKeyBundleMock)
    val privateKeysetResult = F8eSpendingKeysetMock.copy(
      privateWalletRootXpub = "xpub-test-private-wallet"
    )
    createKeysetClient.createKeysetResult = Ok(privateKeysetResult)

    val createKeysetState = MigrationProgress.CreateNewKeyset.PrivateWalletMigration(
      currentKeybox = mockAccount.keybox,
      newHwSpendingKey = mockNewHwKeys.spendingKey,
      hwProofOfPossession = mockProofOfPossession,
      ssek = SsekFake,
      sealedSsek = mockSealedSsek
    )

    val result = service.proceed(state = createKeysetState)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.AuthKeyRotation>()

    // handleCreateNewKeyset propagates the updated keybox (with newKeyset) so that all
    // downstream states carry the correct keybox without needing per-handler DB re-reads.
    state.currentKeybox.activeSpendingKeyset.shouldBe(state.newKeyset)

    // Verify DAO state was updated
    val daoState = privateWalletMigrationDao.state.value.get().shouldNotBeNull()
    daoState.newHardwareKey.shouldBe(mockNewHwKeys.spendingKey)
    daoState.newAppKey.shouldBe(AppKeyBundleMock.spendingKey)
    daoState.newServerKey.shouldBe(privateKeysetResult)
    daoState.keysetLocalId.shouldBe("uuid-0")
    // No flags should be set yet
    daoState.descriptorBackupCompleted.shouldBe(false)
    daoState.serverKeysetActivated.shouldBe(false)
    daoState.cloudBackupCompleted.shouldBe(false)
    daoState.sweepCompleted.shouldBe(false)

    // Verify keyboxDao received the updated keybox
    val savedKeybox = keyboxDao.activeKeybox.value.get().shouldNotBeNull()
    savedKeybox.activeSpendingKeyset.shouldBe(state.newKeyset)
  }

  test("proceed from CreateNewKeyset resumes with existing app key") {
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    // App key is already saved, so we shouldn't generate a new one
    appKeysGenerator.keyBundleResult = Err(RuntimeException("Should not be called!"))

    val privateKeysetResult = F8eSpendingKeysetMock.copy(
      privateWalletRootXpub = "xpub-test-private-wallet"
    )
    createKeysetClient.createKeysetResult = Ok(privateKeysetResult)

    val createKeysetState = MigrationProgress.CreateNewKeyset.PrivateWalletMigration(
      currentKeybox = mockAccount.keybox,
      newHwSpendingKey = mockNewHwKeys.spendingKey,
      hwProofOfPossession = mockProofOfPossession,
      ssek = SsekFake,
      sealedSsek = mockSealedSsek
    )

    val result = service.proceed(state = createKeysetState)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.AuthKeyRotation>()

    // Verify we used the existing app key in the state transition
    state.newKeyset.appKey.shouldBe(AppKeyBundleMock.spendingKey)
  }

  test("proceed from CreateNewKeyset fails when app key generation fails") {
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    val keyGenerationError = RuntimeException("Key generation failed")
    appKeysGenerator.keyBundleResult = Err(keyGenerationError)

    val createKeysetState = MigrationProgress.CreateNewKeyset.PrivateWalletMigration(
      currentKeybox = mockAccount.keybox,
      newHwSpendingKey = mockNewHwKeys.spendingKey,
      hwProofOfPossession = mockProofOfPossession,
      ssek = SsekFake,
      sealedSsek = mockSealedSsek
    )

    val result = service.proceed(state = createKeysetState)

    result.shouldBeErrOfType<MigrationError.AppKeyGenerationFailed>()
    val error = result.error as MigrationError.AppKeyGenerationFailed
    error.cause.shouldBe(keyGenerationError)
  }

  test("proceed from CreateNewKeyset fails when server keyset creation fails") {
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    appKeysGenerator.keyBundleResult = Ok(AppKeyBundleMock)
    val networkError = HttpError.UnhandledException(RuntimeException("Network error"))
    createKeysetClient.createKeysetResult = Err(networkError)

    val createKeysetState = MigrationProgress.CreateNewKeyset.PrivateWalletMigration(
      currentKeybox = mockAccount.keybox,
      newHwSpendingKey = mockNewHwKeys.spendingKey,
      hwProofOfPossession = mockProofOfPossession,
      ssek = SsekFake,
      sealedSsek = mockSealedSsek
    )

    val result = service.proceed(state = createKeysetState)

    result.shouldBeErrOfType<MigrationError.ServerKeysetCreationFailed>()
  }

  test("proceed from ServerKeysetActivation activates keyset on server") {
    accountService.setActiveAccount(mockAccount)

    // Set up the DAO state first
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId("uuid-0")
    privateWalletMigrationDao.setDescriptorBackupComplete()

    val newKeyset = SpendingKeysetMock.copy(
      localId = "uuid-0",
      appKey = AppKeyBundleMock.spendingKey,
      hardwareKey = mockNewHwKeys.spendingKey,
      f8eSpendingKeyset = F8eSpendingKeysetMock
    )

    val serverActivationState = MigrationProgress.ServerKeysetActivation(
      type = MigrationType.PrivateWalletMigration,
      currentKeybox = mockAccount.keybox,
      newKeyset = newKeyset,
      hwProofOfPossession = mockProofOfPossession
    )

    val result = service.proceed(state = serverActivationState)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    // Since requiresNewAuthKeys is false for PrivateWalletMigration, it should skip AuthKeyRotation
    state.shouldBeInstanceOf<MigrationProgress.CloudBackup>()

    // Verify DAO was updated - only serverKeysetActivated should change
    val daoState = privateWalletMigrationDao.state.value.get().shouldNotBeNull()
    daoState.newHardwareKey.shouldBe(mockNewHwKeys.spendingKey)
    daoState.newAppKey.shouldBe(AppKeyBundleMock.spendingKey)
    daoState.newServerKey.shouldBe(F8eSpendingKeysetMock)
    daoState.keysetLocalId.shouldBe("uuid-0")
    daoState.descriptorBackupCompleted.shouldBe(true)
    daoState.serverKeysetActivated.shouldBe(true)
    daoState.cloudBackupCompleted.shouldBe(false)
    daoState.sweepCompleted.shouldBe(false)
  }

  test("proceed from CloudBackup marks backup complete and transitions") {
    accountService.setActiveAccount(mockAccount)
    val newKeyset = SpendingKeysetMock.copy(localId = "uuid-0")

    val cloudBackupState = MigrationProgress.CloudBackup(
      type = MigrationType.PrivateWalletMigration,
      currentKeybox = mockAccount.keybox,
      newKeyset = newKeyset
    )

    // Need to set up the DAO state first
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId("uuid-0")
    privateWalletMigrationDao.setDescriptorBackupComplete()
    privateWalletMigrationDao.setServerKeysetActive()

    val result = service.proceed(state = cloudBackupState)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    // Since requiresServerSweep is false for PrivateWalletMigration, it should go to LocalKeyboxActivation
    state.shouldBeInstanceOf<MigrationProgress.LocalKeyboxActivation>()

    // Verify DAO was updated - only cloudBackupCompleted should change
    val daoState = privateWalletMigrationDao.state.value.get().shouldNotBeNull()
    daoState.descriptorBackupCompleted.shouldBe(true)
    daoState.serverKeysetActivated.shouldBe(true)
    daoState.cloudBackupCompleted.shouldBe(true)
    daoState.sweepCompleted.shouldBe(false)
  }

  test("proceed from LocalKeyboxActivation completes migration") {
    accountService.setActiveAccount(mockAccount)
    val newKeyset = SpendingKeysetMock.copy(localId = "uuid-0")

    val localActivationState = MigrationProgress.LocalKeyboxActivation(
      type = MigrationType.PrivateWalletMigration,
      currentKeybox = mockAccount.keybox,
      newKeyset = newKeyset
    )

    // Set up the DAO state
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId("uuid-0")
    privateWalletMigrationDao.setDescriptorBackupComplete()
    privateWalletMigrationDao.setServerKeysetActive()
    privateWalletMigrationDao.setCloudBackupComplete()

    val result = service.proceed(state = localActivationState)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.Completed>()
    state.type.shouldBe(MigrationType.PrivateWalletMigration)

    // Verify all DAO flags are set after completing migration
    val daoState = privateWalletMigrationDao.state.value.get().shouldNotBeNull()
    daoState.newHardwareKey.shouldBe(mockNewHwKeys.spendingKey)
    daoState.newAppKey.shouldBe(AppKeyBundleMock.spendingKey)
    daoState.newServerKey.shouldBe(F8eSpendingKeysetMock)
    daoState.keysetLocalId.shouldBe("uuid-0")
    daoState.descriptorBackupCompleted.shouldBe(true)
    daoState.serverKeysetActivated.shouldBe(true)
    daoState.cloudBackupCompleted.shouldBe(true)
    daoState.sweepCompleted.shouldBe(true)
  }

  test("proceed from Completed returns Completed") {
    val completedState = MigrationProgress.Completed(MigrationType.PrivateWalletMigration)

    val result = service.proceed(state = completedState)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.Completed>()
  }

  test("proceed from DescriptorBackup fails when listKeysets fails") {
    accountService.setActiveAccount(mockAccount)
    val newKeyset = SpendingKeysetMock.copy(localId = "uuid-0")

    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)

    // Make listKeysets fail
    listKeysetsClient.result = Err(
      build.wallet.ktor.result.HttpError.UnhandledException(RuntimeException("Network error"))
    )

    val descriptorBackupState = MigrationProgress.DescriptorBackup(
      type = MigrationType.PrivateWalletMigration,
      currentKeybox = mockAccount.keybox,
      newKeyset = newKeyset,
      hwProofOfPossession = mockProofOfPossession,
      ssek = SsekFake,
      sealedSsek = mockSealedSsek
    )

    val result = service.proceed(state = descriptorBackupState)

    result.shouldBeErrOfType<MigrationError.DescriptorBackupFailed>()
  }

  test("resume returns CloudBackup when descriptor backup and server keyset are complete") {
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId("uuid-0")
    privateWalletMigrationDao.setDescriptorBackupComplete()
    privateWalletMigrationDao.setServerKeysetActive()

    val result = service.resume(MigrationType.PrivateWalletMigration)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.CloudBackup>()
    state.newKeyset.localId.shouldBe("uuid-0")
  }

  test("resume returns LocalKeyboxActivation when cloud backup is complete") {
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId("uuid-0")
    privateWalletMigrationDao.setDescriptorBackupComplete()
    privateWalletMigrationDao.setServerKeysetActive()
    privateWalletMigrationDao.setCloudBackupComplete()

    val result = service.resume(MigrationType.PrivateWalletMigration)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.LocalKeyboxActivation>()
    state.newKeyset.localId.shouldBe("uuid-0")
  }

  test("proceed from CreateNewKeyset preserves keyset data for later migration steps") {
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    appKeysGenerator.keyBundleResult = Ok(AppKeyBundleMock)
    val privateKeysetResult = F8eSpendingKeysetMock.copy(
      privateWalletRootXpub = "xpub-test-private-wallet"
    )
    createKeysetClient.createKeysetResult = Ok(privateKeysetResult)

    val createKeysetState = MigrationProgress.CreateNewKeyset.PrivateWalletMigration(
      currentKeybox = mockAccount.keybox,
      newHwSpendingKey = mockNewHwKeys.spendingKey,
      hwProofOfPossession = mockProofOfPossession,
      ssek = SsekFake,
      sealedSsek = mockSealedSsek
    )

    val result = service.proceed(state = createKeysetState)

    result.shouldBeOk()
    val nextState = result.get().shouldNotBeNull()
    nextState.shouldBeInstanceOf<MigrationProgress.AuthKeyRotation>()

    // Verify the keyset in the next state has correct data for sweep
    nextState.newKeyset.appKey.shouldBe(AppKeyBundleMock.spendingKey)
    nextState.newKeyset.hardwareKey.shouldBe(mockNewHwKeys.spendingKey)
    nextState.newKeyset.f8eSpendingKeyset.shouldBe(privateKeysetResult)
    nextState.newKeyset.f8eSpendingKeyset.privateWalletRootXpub.shouldBe("xpub-test-private-wallet")

    // Updated keybox (with newKeyset) is propagated to all downstream states.
    nextState.currentKeybox.activeSpendingKeyset.shouldBe(nextState.newKeyset)
  }

  test("proceed from CreateNewKeyset resumes with existing server keyset") {
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId("existing-keyset-id")

    // Should not call create keyset since it already exists
    createKeysetClient.createKeysetResult = Err(HttpError.UnhandledException(RuntimeException("Should not be called!")))

    val createKeysetState = MigrationProgress.CreateNewKeyset.PrivateWalletMigration(
      currentKeybox = mockAccount.keybox,
      newHwSpendingKey = mockNewHwKeys.spendingKey,
      hwProofOfPossession = mockProofOfPossession,
      ssek = SsekFake,
      sealedSsek = mockSealedSsek
    )

    val result = service.proceed(state = createKeysetState)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.AuthKeyRotation>()

    // Verify we used the existing keyset from DAO
    state.newKeyset.localId.shouldBe("existing-keyset-id")
    state.newKeyset.f8eSpendingKeyset.shouldBe(F8eSpendingKeysetMock)

    // Updated keybox (with newKeyset) is propagated to all downstream states.
    state.currentKeybox.activeSpendingKeyset.shouldBe(state.newKeyset)
  }

  test("W3Upgrade proceed from CreateNewKeyset stores sealed SSEK for resumed descriptor backup") {
    accountService.setActiveAccount(mockAccount)
    appKeysGenerator.keyBundleResult = Ok(AppKeyBundleMock)
    createKeysetClient.createKeysetResult = Ok(F8eSpendingKeysetMock)

    val createKeysetState = MigrationProgress.CreateNewKeyset.W3Upgrade(
      oldDeviceSerial = "old-device-serial",
      oldHardwareFingerprint = "old-hw-fingerprint",
      newDeviceSerial = "new-device-serial",
      currentKeybox = mockAccount.keybox,
      newHwSpendingKey = mockNewHwKeys.spendingKey,
      hwProofOfPossession = HwFactorProofOfPossession(""),
      ssek = SsekFake,
      sealedSsek = mockSealedSsek
    )

    val result = service.proceed(state = createKeysetState)

    result.shouldBeOk()
    result.get().shouldNotBeNull().shouldBeInstanceOf<MigrationProgress.AuthKeyRotation>()
    onboardingKeyboxSealedSsekDao.get().get().shouldBe(mockSealedSsek)
    w3UpgradeCheckpointWriter.persistedNewDeviceSerial.shouldBe("new-device-serial")
    w3UpgradeCheckpointWriter.persistedUnlockInfo.shouldBe(build.wallet.firmware.UnlockInfo.ONBOARDING_DEFAULT)
    val daoState = w3UpgradeDao.state.value.get().shouldNotBeNull()
    daoState.oldDeviceSerial.shouldBe("old-device-serial")
    daoState.oldHardwareFingerprint.shouldBe("old-hw-fingerprint")
    daoState.newServerKey.shouldBe(F8eSpendingKeysetMock)
  }

  test("W3Upgrade resume returns AuthKeyRotation after successful CreateNewKeyset checkpoint") {
    accountService.setActiveAccount(mockAccount)
    appKeysGenerator.keyBundleResult = Ok(AppKeyBundleMock)
    createKeysetClient.createKeysetResult = Ok(F8eSpendingKeysetMock)

    val createKeysetState = MigrationProgress.CreateNewKeyset.W3Upgrade(
      oldDeviceSerial = "old-device-serial",
      oldHardwareFingerprint = "old-hw-fingerprint",
      newDeviceSerial = "new-device-serial",
      currentKeybox = mockAccount.keybox,
      newHwSpendingKey = mockNewHwKeys.spendingKey,
      hwProofOfPossession = HwFactorProofOfPossession(""),
      ssek = SsekFake,
      sealedSsek = mockSealedSsek
    )

    service.proceed(state = createKeysetState).shouldBeOkOfType<MigrationProgress.AuthKeyRotation>()

    val persistedKeybox = keyboxDao.activeKeybox.value.get().shouldNotBeNull()
    accountService.setActiveAccount(mockAccount.copy(keybox = persistedKeybox))

    val resumed = service.resume(MigrationType.W3Upgrade)

    resumed.shouldBeOk()
    val resumedState = resumed.get().shouldNotBeNull().shouldBeInstanceOf<MigrationProgress.AuthKeyRotation>()
    resumedState.currentKeybox.shouldBe(persistedKeybox)
    resumedState.newKeyset.localId.shouldBe("uuid-0")
    resumedState.newKeyset.f8eSpendingKeyset.shouldBe(F8eSpendingKeysetMock)
  }

  test("W3Upgrade proceed from CreateNewKeyset fails before advancing when sealed SSEK persistence fails") {
    accountService.setActiveAccount(mockAccount)
    onboardingKeyboxSealedSsekDao.shouldFailToStore = true
    appKeysGenerator.keyBundleResult = Ok(AppKeyBundleMock)
    createKeysetClient.createKeysetResult = Ok(F8eSpendingKeysetMock)

    val createKeysetState = MigrationProgress.CreateNewKeyset.W3Upgrade(
      oldDeviceSerial = "old-device-serial",
      oldHardwareFingerprint = "old-hw-fingerprint",
      newDeviceSerial = "new-device-serial",
      currentKeybox = mockAccount.keybox,
      newHwSpendingKey = mockNewHwKeys.spendingKey,
      hwProofOfPossession = HwFactorProofOfPossession(""),
      ssek = SsekFake,
      sealedSsek = mockSealedSsek
    )

    val result = service.proceed(state = createKeysetState)

    result.shouldBeErrOfType<MigrationError.StatePersistenceFailed>()
    w3UpgradeDao.state.value.get().shouldBe(null)
    keyboxDao.activeKeybox.value.get().shouldBe(null)
  }

  test("W3Upgrade proceed from CreateNewKeyset leaves local W3 checkpoint untouched when checkpoint write fails") {
    accountService.setActiveAccount(mockAccount)
    appKeysGenerator.keyBundleResult = Ok(AppKeyBundleMock)
    createKeysetClient.createKeysetResult = Ok(F8eSpendingKeysetMock)
    w3UpgradeCheckpointWriter.shouldFailPersist = true

    val createKeysetState = MigrationProgress.CreateNewKeyset.W3Upgrade(
      oldDeviceSerial = "old-device-serial",
      oldHardwareFingerprint = "old-hw-fingerprint",
      newDeviceSerial = "new-device-serial",
      currentKeybox = mockAccount.keybox,
      newHwSpendingKey = mockNewHwKeys.spendingKey,
      hwProofOfPossession = HwFactorProofOfPossession(""),
      ssek = SsekFake,
      sealedSsek = mockSealedSsek
    )

    val result = service.proceed(state = createKeysetState)

    result.shouldBeErrOfType<MigrationError.StatePersistenceFailed>()
    w3UpgradeDao.state.value.get().shouldBe(null)
    keyboxDao.activeKeybox.value.get().shouldBe(null)
    onboardingKeyboxSealedSsekDao.get().get().shouldBe(null)
  }

  test("proceed from ServerKeysetActivation fails when server activation fails") {
    accountService.setActiveAccount(mockAccount)

    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId("uuid-0")
    privateWalletMigrationDao.setDescriptorBackupComplete()

    val networkError = HttpError.UnhandledException(RuntimeException("Server error"))
    setActiveSpendingKeysetF8eClient.setResult = Err(networkError)

    val newKeyset = SpendingKeysetMock.copy(
      localId = "uuid-0",
      appKey = AppKeyBundleMock.spendingKey,
      hardwareKey = mockNewHwKeys.spendingKey,
      f8eSpendingKeyset = F8eSpendingKeysetMock
    )

    val serverActivationState = MigrationProgress.ServerKeysetActivation(
      type = MigrationType.PrivateWalletMigration,
      currentKeybox = mockAccount.keybox,
      newKeyset = newKeyset,
      hwProofOfPossession = mockProofOfPossession
    )

    val result = service.proceed(state = serverActivationState)

    result.shouldBeErrOfType<MigrationError.ServerKeysetActivationFailed>()
  }

  test("proceed from DescriptorBackup succeeds and transitions to ServerKeysetActivation") {
    // Add a local-only keyset that won't be in backup results to exercise keyset pruning
    val localOnlyKeyset = SpendingKeysetMock.copy(
      localId = "local-only-keyset-id",
      f8eSpendingKeyset = F8eSpendingKeysetMock.copy(keysetId = "local-only-f8e-keyset-id")
    )
    val currentKeybox = mockAccount.keybox.copy(
      canUseKeyboxKeysets = false,
      keysets = mockAccount.keybox.keysets + localOnlyKeyset
    )
    accountService.setActiveAccount(mockAccount.copy(keybox = currentKeybox))

    val newKeyset = SpendingKeysetMock.copy(
      localId = currentKeybox.activeSpendingKeyset.localId, // Use existing keyset ID so account lookup works
      appKey = AppKeyBundleMock.spendingKey,
      hardwareKey = mockNewHwKeys.spendingKey,
      f8eSpendingKeyset = F8eSpendingKeysetPrivateWalletMock.copy(keysetId = "new-f8e-spending-keyset-id")
    )
    val uploadedKeysets = listOf(currentKeybox.activeSpendingKeyset, newKeyset)
    descriptorBackupService.uploadDescriptorBackupsResult = Ok(uploadedKeysets)
    val expectedKeybox = currentKeybox.copy(
      activeSpendingKeyset = newKeyset,
      keysets = listOf(currentKeybox.activeSpendingKeyset, newKeyset),
      canUseKeyboxKeysets = true
    )

    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId(newKeyset.localId)

    val descriptorBackupState = MigrationProgress.DescriptorBackup(
      type = MigrationType.PrivateWalletMigration,
      currentKeybox = currentKeybox,
      newKeyset = newKeyset,
      hwProofOfPossession = mockProofOfPossession,
      ssek = SsekFake,
      sealedSsek = mockSealedSsek
    )

    val result = service.proceed(state = descriptorBackupState)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.ServerKeysetActivation>()
    state.currentKeybox.shouldBe(expectedKeybox)

    // Verify descriptor backup was marked complete, no other flags set
    val daoState = privateWalletMigrationDao.state.value.get().shouldNotBeNull()
    daoState.newHardwareKey.shouldBe(mockNewHwKeys.spendingKey)
    daoState.newAppKey.shouldBe(AppKeyBundleMock.spendingKey)
    daoState.newServerKey.shouldBe(F8eSpendingKeysetMock)
    daoState.descriptorBackupCompleted.shouldBe(true)
    daoState.serverKeysetActivated.shouldBe(false)
    daoState.cloudBackupCompleted.shouldBe(false)
    daoState.sweepCompleted.shouldBe(false)

    // Verify SSEK was stored
    ssekDao.get(mockSealedSsek).get().shouldBe(SsekFake)
    keyboxDao.activeKeybox.value.get().shouldBe(expectedKeybox)
  }

  test("state transitions carry credentials through the flow") {
    // Start from NotStarted and verify credentials flow through
    val notStartedState = MigrationProgress.NotStarted(MigrationType.PrivateWalletMigration)

    val createKeysetState = notStartedState.next(
      currentKeybox = mockAccount.keybox,
      newHwSpendingKey = mockNewHwKeys.spendingKey,
      hwProofOfPossession = mockProofOfPossession,
      ssek = SsekFake,
      sealedSsek = mockSealedSsek
    )

    val authKeyRotationState = createKeysetState.next(
      newKeyset = SpendingKeysetMock,
      updatedKeybox = mockAccount.keybox
    )

    authKeyRotationState.hwProofOfPossession.shouldBe(mockProofOfPossession)
    authKeyRotationState.sealedSsek.shouldBe(mockSealedSsek)
    authKeyRotationState.ssek.shouldBe(SsekFake)

    val descriptorBackupState = authKeyRotationState.next(currentKeybox = mockAccount.keybox)

    descriptorBackupState.hwProofOfPossession.shouldBe(mockProofOfPossession)
    descriptorBackupState.sealedSsek.shouldBe(mockSealedSsek)
    descriptorBackupState.ssek.shouldBe(SsekFake)

    val serverActivationState = descriptorBackupState.next()

    serverActivationState.hwProofOfPossession.shouldBe(mockProofOfPossession)
  }

  // -- Private wallet migration: AuthKeyRotation no-op --

  test("proceed from AuthKeyRotation for private wallet migration is a no-op and transitions to DescriptorBackup") {
    accountService.setActiveAccount(mockAccount)
    val newKeyset = SpendingKeysetMock.copy(
      localId = "uuid-0",
      appKey = AppKeyBundleMock.spendingKey,
      hardwareKey = mockNewHwKeys.spendingKey,
      f8eSpendingKeyset = F8eSpendingKeysetMock
    )

    val authKeyRotationState = MigrationProgress.AuthKeyRotation(
      type = MigrationType.PrivateWalletMigration,
      currentKeybox = mockAccount.keybox,
      newKeyset = newKeyset,
      hwProofOfPossession = mockProofOfPossession,
      ssek = SsekFake,
      sealedSsek = mockSealedSsek
    )

    val result = service.proceed(state = authKeyRotationState)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.DescriptorBackup>()
    state.type.shouldBe(MigrationType.PrivateWalletMigration)
    // Credentials should be passed through
    state.hwProofOfPossession.shouldBe(mockProofOfPossession)
    state.ssek.shouldBe(SsekFake)
    state.sealedSsek.shouldBe(mockSealedSsek)
    state.currentKeybox.shouldBe(mockAccount.keybox)
    state.newKeyset.shouldBe(newKeyset)
  }

  // -- Private wallet migration: resume at additional intermediate states --

  test("resume returns NotStarted when hardware and app keys exist but no server key") {
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    // No server key or keyset local ID

    val result = service.resume(MigrationType.PrivateWalletMigration)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.NotStarted>()
    state.type.shouldBe(MigrationType.PrivateWalletMigration)
  }

  test("resume returns NotStarted when keyset exists but keyset local ID is missing") {
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    // No keyset local ID

    val result = service.resume(MigrationType.PrivateWalletMigration)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.NotStarted>()
    state.type.shouldBe(MigrationType.PrivateWalletMigration)
  }

  test("resume returns NotStarted when descriptor backup is complete but server keyset is not activated") {
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId("uuid-0")
    privateWalletMigrationDao.setDescriptorBackupComplete()
    // Server keyset NOT activated

    val result = service.resume(MigrationType.PrivateWalletMigration)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.NotStarted>()
    state.type.shouldBe(MigrationType.PrivateWalletMigration)
  }

  // -- Private wallet migration: resume verifies keyset data --

  test("resume at CloudBackup reconstructs keyset from DAO state") {
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId("my-keyset-id")
    privateWalletMigrationDao.setDescriptorBackupComplete()
    privateWalletMigrationDao.setServerKeysetActive()

    val result = service.resume(MigrationType.PrivateWalletMigration)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.CloudBackup>()
    state.newKeyset.localId.shouldBe("my-keyset-id")
    state.newKeyset.appKey.shouldBe(AppKeyBundleMock.spendingKey)
    state.newKeyset.hardwareKey.shouldBe(mockNewHwKeys.spendingKey)
    state.newKeyset.f8eSpendingKeyset.shouldBe(F8eSpendingKeysetMock)
    state.currentKeybox.shouldBe(mockAccount.keybox)
  }

  test("resume at LocalKeyboxActivation reconstructs keyset from DAO state") {
    accountService.setActiveAccount(mockAccount)
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId("my-keyset-id")
    privateWalletMigrationDao.setDescriptorBackupComplete()
    privateWalletMigrationDao.setServerKeysetActive()
    privateWalletMigrationDao.setCloudBackupComplete()

    val result = service.resume(MigrationType.PrivateWalletMigration)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.LocalKeyboxActivation>()
    state.newKeyset.localId.shouldBe("my-keyset-id")
    state.newKeyset.appKey.shouldBe(AppKeyBundleMock.spendingKey)
    state.newKeyset.hardwareKey.shouldBe(mockNewHwKeys.spendingKey)
    state.newKeyset.f8eSpendingKeyset.shouldBe(F8eSpendingKeysetMock)
    state.currentKeybox.shouldBe(mockAccount.keybox)
  }

  // -- Private wallet migration: clearMigration --

  test("clearMigration clears private wallet migration state") {
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId("uuid-0")
    privateWalletMigrationDao.setDescriptorBackupComplete()

    // Verify DAO has state
    privateWalletMigrationDao.state.value.get().shouldNotBeNull()

    service.clearMigration(MigrationType.PrivateWalletMigration)

    // Verify DAO is cleared
    privateWalletMigrationDao.state.value.get().shouldBe(null)
  }

  // -- Private wallet migration: estimateMigrationFees --

  test("estimateMigrationFees returns sweep total fees for private wallet migration") {
    val mockSweep = Sweep(
      unsignedPsbts = setOf(
        SweepPsbt(PsbtMock, SweepSignaturePlan.AppAndServer, SpendingKeysetMock, "bc1qtest")
      )
    )
    sweepService.estimateSweepWithMockDestinationResult = Ok(mockSweep)

    val result = service.estimateMigrationFees(mockAccount, oldHardwareFingerprint = null)

    result.shouldBeOk(BitcoinMoney.sats(10_000L))
  }

  test("estimateMigrationFees returns InsufficientFundsForMigration when no funds to sweep") {
    sweepService.estimateSweepWithMockDestinationResult = Err(SweepService.SweepError.NoFundsToSweep)

    val result = service.estimateMigrationFees(mockAccount, oldHardwareFingerprint = null)

    result.shouldBeErrOfType<MigrationError.InsufficientFundsForMigration>()
  }

  test("estimateMigrationFees returns FeeEstimationFailed when sweep generation fails") {
    sweepService.estimateSweepWithMockDestinationResult =
      Err(SweepService.SweepError.SweepGenerationFailed(Error("generation error")))

    val result = service.estimateMigrationFees(mockAccount, oldHardwareFingerprint = null)

    result.shouldBeErrOfType<MigrationError.FeeEstimationFailed>()
  }

  // -- Private wallet migration: full end-to-end flow with DAO verification at each step --

  test("private wallet migration full flow from NotStarted to Completed") {
    accountService.setActiveAccount(mockAccount)
    appKeysGenerator.keyBundleResult = Ok(AppKeyBundleMock)
    val privateKeysetResult = F8eSpendingKeysetMock.copy(
      privateWalletRootXpub = "xpub-test-private-wallet"
    )
    createKeysetClient.createKeysetResult = Ok(privateKeysetResult)

    // Before: DAO should be empty
    privateWalletMigrationDao.state.value.get().shouldBe(null)

    // Step 1: resume returns NotStarted
    val resumeResult = service.resume(MigrationType.PrivateWalletMigration)
    resumeResult.shouldBeOk()
    val notStarted = resumeResult.get().shouldNotBeNull()
    notStarted.shouldBeInstanceOf<MigrationProgress.NotStarted>()

    // Step 2: NotStarted → CreateNewKeyset via next()
    val createKeysetState = notStarted.next(
      currentKeybox = mockAccount.keybox,
      newHwSpendingKey = mockNewHwKeys.spendingKey,
      hwProofOfPossession = mockProofOfPossession,
      ssek = SsekFake,
      sealedSsek = mockSealedSsek
    )

    // Step 3: proceed CreateNewKeyset → AuthKeyRotation
    val createResult = service.proceed(state = createKeysetState)
    createResult.shouldBeOk()
    val authRotation = createResult.get().shouldNotBeNull()
    authRotation.shouldBeInstanceOf<MigrationProgress.AuthKeyRotation>()

    // Verify DAO state after CreateNewKeyset: keys saved, no flags set yet
    val daoAfterCreate = privateWalletMigrationDao.state.value.get().shouldNotBeNull()
    daoAfterCreate.newHardwareKey.shouldBe(mockNewHwKeys.spendingKey)
    daoAfterCreate.newAppKey.shouldBe(AppKeyBundleMock.spendingKey)
    daoAfterCreate.newServerKey.shouldBe(privateKeysetResult)
    daoAfterCreate.keysetLocalId.shouldBe("uuid-0")
    daoAfterCreate.descriptorBackupCompleted.shouldBe(false)
    daoAfterCreate.serverKeysetActivated.shouldBe(false)
    daoAfterCreate.cloudBackupCompleted.shouldBe(false)
    daoAfterCreate.sweepCompleted.shouldBe(false)

    // Verify keyboxDao was updated with new keyset as active
    val savedKeybox = keyboxDao.activeKeybox.value.get().shouldNotBeNull()
    savedKeybox.activeSpendingKeyset.localId.shouldBe("uuid-0")
    savedKeybox.activeSpendingKeyset.appKey.shouldBe(AppKeyBundleMock.spendingKey)
    savedKeybox.activeSpendingKeyset.hardwareKey.shouldBe(mockNewHwKeys.spendingKey)
    savedKeybox.activeSpendingKeyset.f8eSpendingKeyset.shouldBe(privateKeysetResult)

    // Step 4: proceed AuthKeyRotation → DescriptorBackup (no-op for private wallet)
    val authResult = service.proceed(state = authRotation)
    authResult.shouldBeOk()
    val descriptorBackup = authResult.get().shouldNotBeNull()
    descriptorBackup.shouldBeInstanceOf<MigrationProgress.DescriptorBackup>()

    // Verify DAO state unchanged after AuthKeyRotation (no-op)
    val daoAfterAuth = privateWalletMigrationDao.state.value.get().shouldNotBeNull()
    daoAfterAuth.newHardwareKey.shouldBe(mockNewHwKeys.spendingKey)
    daoAfterAuth.newAppKey.shouldBe(AppKeyBundleMock.spendingKey)
    daoAfterAuth.newServerKey.shouldBe(privateKeysetResult)
    daoAfterAuth.keysetLocalId.shouldBe("uuid-0")
    daoAfterAuth.descriptorBackupCompleted.shouldBe(false)
    daoAfterAuth.serverKeysetActivated.shouldBe(false)
    daoAfterAuth.cloudBackupCompleted.shouldBe(false)
    daoAfterAuth.sweepCompleted.shouldBe(false)

    // Step 5: proceed DescriptorBackup → ServerKeysetActivation
    val descriptorResult = service.proceed(state = descriptorBackup)
    descriptorResult.shouldBeOk()
    val serverActivation = descriptorResult.get().shouldNotBeNull()
    serverActivation.shouldBeInstanceOf<MigrationProgress.ServerKeysetActivation>()

    // Verify DAO state: only descriptorBackupCompleted should be set
    val daoAfterDescriptor = privateWalletMigrationDao.state.value.get().shouldNotBeNull()
    daoAfterDescriptor.newHardwareKey.shouldBe(mockNewHwKeys.spendingKey)
    daoAfterDescriptor.newAppKey.shouldBe(AppKeyBundleMock.spendingKey)
    daoAfterDescriptor.newServerKey.shouldBe(privateKeysetResult)
    daoAfterDescriptor.keysetLocalId.shouldBe("uuid-0")
    daoAfterDescriptor.descriptorBackupCompleted.shouldBe(true)
    daoAfterDescriptor.serverKeysetActivated.shouldBe(false)
    daoAfterDescriptor.cloudBackupCompleted.shouldBe(false)
    daoAfterDescriptor.sweepCompleted.shouldBe(false)

    // Verify SSEK was persisted during descriptor backup
    ssekDao.get(mockSealedSsek).get().shouldBe(SsekFake)

    // Step 6: proceed ServerKeysetActivation → CloudBackup
    val serverResult = service.proceed(state = serverActivation)
    serverResult.shouldBeOk()
    val cloudBackup = serverResult.get().shouldNotBeNull()
    cloudBackup.shouldBeInstanceOf<MigrationProgress.CloudBackup>()

    // Verify DAO state: descriptorBackup + serverKeyset flags set
    val daoAfterServer = privateWalletMigrationDao.state.value.get().shouldNotBeNull()
    daoAfterServer.descriptorBackupCompleted.shouldBe(true)
    daoAfterServer.serverKeysetActivated.shouldBe(true)
    daoAfterServer.cloudBackupCompleted.shouldBe(false)
    daoAfterServer.sweepCompleted.shouldBe(false)

    // Step 7: proceed CloudBackup → LocalKeyboxActivation
    val cloudResult = service.proceed(state = cloudBackup)
    cloudResult.shouldBeOk()
    val localActivation = cloudResult.get().shouldNotBeNull()
    localActivation.shouldBeInstanceOf<MigrationProgress.LocalKeyboxActivation>()

    // Verify DAO state: cloud backup now complete, sweep still pending
    val daoAfterCloud = privateWalletMigrationDao.state.value.get().shouldNotBeNull()
    daoAfterCloud.descriptorBackupCompleted.shouldBe(true)
    daoAfterCloud.serverKeysetActivated.shouldBe(true)
    daoAfterCloud.cloudBackupCompleted.shouldBe(true)
    daoAfterCloud.sweepCompleted.shouldBe(false)

    // Step 8: proceed LocalKeyboxActivation → Completed
    val localResult = service.proceed(state = localActivation)
    localResult.shouldBeOk()
    val completed = localResult.get().shouldNotBeNull()
    completed.shouldBeInstanceOf<MigrationProgress.Completed>()
    completed.type.shouldBe(MigrationType.PrivateWalletMigration)

    // Verify final DAO state: everything set
    val finalDaoState = privateWalletMigrationDao.state.value.get().shouldNotBeNull()
    finalDaoState.newHardwareKey.shouldBe(mockNewHwKeys.spendingKey)
    finalDaoState.newAppKey.shouldBe(AppKeyBundleMock.spendingKey)
    finalDaoState.newServerKey.shouldBe(privateKeysetResult)
    finalDaoState.keysetLocalId.shouldBe("uuid-0")
    finalDaoState.descriptorBackupCompleted.shouldBe(true)
    finalDaoState.serverKeysetActivated.shouldBe(true)
    finalDaoState.cloudBackupCompleted.shouldBe(true)
    finalDaoState.sweepCompleted.shouldBe(true)
  }

  // -- Private wallet migration: DescriptorBackup stores SSEK --

  test("proceed from DescriptorBackup for private wallet stores SSEK before uploading") {
    accountService.setActiveAccount(mockAccount)

    val newKeyset = SpendingKeysetMock.copy(
      localId = mockAccount.keybox.activeSpendingKeyset.localId,
      appKey = AppKeyBundleMock.spendingKey,
      hardwareKey = mockNewHwKeys.spendingKey,
      f8eSpendingKeyset = F8eSpendingKeysetPrivateWalletMock.copy(keysetId = "new-f8e-spending-keyset-id")
    )
    descriptorBackupService.uploadDescriptorBackupsResult = Ok(
      listOf(mockAccount.keybox.activeSpendingKeyset, newKeyset)
    )

    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId(newKeyset.localId)

    val descriptorBackupState = MigrationProgress.DescriptorBackup(
      type = MigrationType.PrivateWalletMigration,
      currentKeybox = mockAccount.keybox,
      newKeyset = newKeyset,
      hwProofOfPossession = mockProofOfPossession,
      ssek = SsekFake,
      sealedSsek = mockSealedSsek
    )

    val result = service.proceed(state = descriptorBackupState)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.ServerKeysetActivation>()

    // Verify SSEK was stored
    ssekDao.get(mockSealedSsek).get().shouldBe(SsekFake)
  }

  // -- Private wallet migration: resuming at CloudBackup then completing --

  test("resuming at CloudBackup and proceeding to Completed") {
    accountService.setActiveAccount(mockAccount)

    // Set up DAO state representing a migration interrupted after server activation
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId("uuid-0")
    privateWalletMigrationDao.setDescriptorBackupComplete()
    privateWalletMigrationDao.setServerKeysetActive()

    // Resume
    val resumeResult = service.resume(MigrationType.PrivateWalletMigration)
    resumeResult.shouldBeOk()
    val cloudBackup = resumeResult.get().shouldNotBeNull()
    cloudBackup.shouldBeInstanceOf<MigrationProgress.CloudBackup>()

    // Proceed CloudBackup → LocalKeyboxActivation
    val cloudResult = service.proceed(state = cloudBackup)
    cloudResult.shouldBeOk()
    val localActivation = cloudResult.get().shouldNotBeNull()
    localActivation.shouldBeInstanceOf<MigrationProgress.LocalKeyboxActivation>()

    // Proceed LocalKeyboxActivation → Completed
    val localResult = service.proceed(state = localActivation)
    localResult.shouldBeOk()
    val completed = localResult.get().shouldNotBeNull()
    completed.shouldBeInstanceOf<MigrationProgress.Completed>()
    completed.type.shouldBe(MigrationType.PrivateWalletMigration)

    // Verify final state
    val daoState = privateWalletMigrationDao.state.value.get().shouldNotBeNull()
    daoState.cloudBackupCompleted.shouldBe(true)
    daoState.sweepCompleted.shouldBe(true)
  }

  // -- Private wallet migration: resuming at LocalKeyboxActivation then completing --

  test("resuming at LocalKeyboxActivation and proceeding to Completed") {
    accountService.setActiveAccount(mockAccount)

    // Set up DAO state representing a migration interrupted after cloud backup
    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId("uuid-0")
    privateWalletMigrationDao.setDescriptorBackupComplete()
    privateWalletMigrationDao.setServerKeysetActive()
    privateWalletMigrationDao.setCloudBackupComplete()

    // Resume
    val resumeResult = service.resume(MigrationType.PrivateWalletMigration)
    resumeResult.shouldBeOk()
    val localActivation = resumeResult.get().shouldNotBeNull()
    localActivation.shouldBeInstanceOf<MigrationProgress.LocalKeyboxActivation>()

    // Proceed LocalKeyboxActivation → Completed
    val localResult = service.proceed(state = localActivation)
    localResult.shouldBeOk()
    val completed = localResult.get().shouldNotBeNull()
    completed.shouldBeInstanceOf<MigrationProgress.Completed>()
    completed.type.shouldBe(MigrationType.PrivateWalletMigration)

    // Verify sweep was marked complete
    val daoState = privateWalletMigrationDao.state.value.get().shouldNotBeNull()
    daoState.sweepCompleted.shouldBe(true)
  }

  // -- Private wallet migration: ServerKeysetActivation marks DAO --

  test("proceed from ServerKeysetActivation marks server keyset as active in DAO") {
    accountService.setActiveAccount(mockAccount)

    privateWalletMigrationDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    privateWalletMigrationDao.saveAppKey(AppKeyBundleMock.spendingKey)
    privateWalletMigrationDao.saveServerKey(F8eSpendingKeysetMock)
    privateWalletMigrationDao.saveKeysetLocalId("uuid-0")
    privateWalletMigrationDao.setDescriptorBackupComplete()

    val newKeyset = SpendingKeysetMock.copy(
      localId = "uuid-0",
      appKey = AppKeyBundleMock.spendingKey,
      hardwareKey = mockNewHwKeys.spendingKey,
      f8eSpendingKeyset = F8eSpendingKeysetMock
    )

    val serverActivationState = MigrationProgress.ServerKeysetActivation(
      type = MigrationType.PrivateWalletMigration,
      currentKeybox = mockAccount.keybox,
      newKeyset = newKeyset,
      hwProofOfPossession = mockProofOfPossession
    )

    // Verify server keyset is NOT active before proceeding
    val daoStateBefore = privateWalletMigrationDao.state.value.get().shouldNotBeNull()
    daoStateBefore.serverKeysetActivated.shouldBe(false)

    val result = service.proceed(state = serverActivationState)
    result.shouldBeOk()

    // Verify DAO was updated
    val daoStateAfter = privateWalletMigrationDao.state.value.get().shouldNotBeNull()
    daoStateAfter.serverKeysetActivated.shouldBe(true)
  }

  test("W3Upgrade resume returns Completed when account already has W3 hardware") {
    accountService.setActiveAccount(FullAccountW3Mock)

    val result = service.resume(MigrationType.W3Upgrade)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.Completed>()
    state.type.shouldBe(MigrationType.W3Upgrade)
  }

  test("W3Upgrade resume returns cloud-restore placeholder when resumed flag is persisted") {
    accountService.setActiveAccount(mockAccount)

    w3UpgradeDao.markResumedFromCloudBackup().shouldBeOk()

    val result = service.resume(MigrationType.W3Upgrade)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull().shouldBeInstanceOf<MigrationProgress.NotStarted>()
    state.type.shouldBe(MigrationType.W3Upgrade)
    state.resumedFromCloudBackup.shouldBe(true)

    val daoState = w3UpgradeDao.state.value.get().shouldNotBeNull()
    daoState.resumedFromCloudBackup.shouldBe(true)
    daoState.newHardwareKey.shouldBe(null)
  }

  test("W3Upgrade resume returns Completed when stale cloud-restore placeholder exists on W3 account") {
    // Regression: a previous interrupted restore left a resumedFromCloudBackup marker,
    // but the account was later successfully restored to W3. The stale placeholder must
    // not re-route the user into the W3 upgrade flow.
    accountService.setActiveAccount(FullAccountW3Mock)
    w3UpgradeDao.markResumedFromCloudBackup()

    val result = service.resume(MigrationType.W3Upgrade)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.Completed>()
    state.type.shouldBe(MigrationType.W3Upgrade)
  }

  test("W3Upgrade resume returns DescriptorBackup after auth rotation completes") {
    accountService.setActiveAccount(rotatedAccount())

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")
    w3UpgradeDao.setAuthKeyRotationComplete()

    val result = service.resume(MigrationType.W3Upgrade)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.DescriptorBackup>()
    state.currentKeybox.shouldBe(rotatedKeybox())
    state.proof.shouldBe(null)
  }

  test("W3Upgrade resumed-from-cloud create keyset persists wrapped SSEK when historical backups are missing") {
    accountService.setActiveAccount(mockAccount)
    listKeysetsClient.result = Ok(
      ListKeysetsResponse(
        keysets = emptyList(),
        wrappedSsek = SealedSsekFake,
        descriptorBackups = listOf(
          DescriptorBackup(
            keysetId = "historical-keyset-id",
            sealedDescriptor = XCiphertext("historical-descriptor"),
            privateWalletRootXpub = null
          )
        ),
        activeKeysetId = mockAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
      )
    )

    val createNewKeysetState = MigrationProgress.CreateNewKeyset.W3Upgrade(
      oldDeviceSerial = "old-device-serial",
      oldHardwareFingerprint = "old-hardware-fingerprint",
      newDeviceSerial = "new-device-serial",
      currentKeybox = mockAccount.keybox,
      newHwSpendingKey = mockNewHwKeys.spendingKey,
      hwProofOfPossession = HwFactorProofOfPossession(""),
      ssek = SsekFake,
      sealedSsek = mockSealedSsek,
      resumedFromCloudBackup = true
    )

    val result = service.proceed(createNewKeysetState)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull().shouldBeInstanceOf<MigrationProgress.AuthKeyRotation>()
    state.resumedFromCloudBackup.shouldBe(true)
    state.sealedSsekForDecryption.shouldBe(SealedSsekFake)

    val daoState = w3UpgradeDao.state.value.get().shouldNotBeNull()
    daoState.sealedSsekForDecryption.shouldBe(SealedSsekFake)
  }

  test("W3Upgrade resume returns ServerKeysetActivation when descriptor backup already completed") {
    accountService.setActiveAccount(rotatedAccount())

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")
    w3UpgradeDao.setAuthKeyRotationComplete()
    w3UpgradeDao.setDescriptorBackupComplete()

    val result = service.resume(MigrationType.W3Upgrade)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull().shouldBeInstanceOf<MigrationProgress.ServerKeysetActivation>()
    state.currentKeybox.shouldBe(rotatedKeybox())
    state.proof.shouldBe(null)
  }

  test("W3Upgrade proceed from AuthKeyRotation rotates recovery and hardware auth and returns DescriptorBackup") {
    val rotatedAuthKeys = w3RotatedAuthKeys()
    val recoveryTokens = AccountAuthTokens(
      accessToken = AccessToken("new-recovery-access-token"),
      refreshToken = RefreshToken("new-recovery-refresh-token"),
      accessTokenExpiresAt = Instant.DISTANT_FUTURE,
      refreshTokenExpiresAt = Instant.DISTANT_FUTURE
    )
    val globalTokens = AccountAuthTokens(
      accessToken = AccessToken("new-global-access-token"),
      refreshToken = RefreshToken("new-global-refresh-token"),
      accessTokenExpiresAt = Instant.DISTANT_FUTURE,
      refreshTokenExpiresAt = Instant.DISTANT_FUTURE
    )
    accountService.setActiveAccount(mockAccount)
    keyboxDao.rotateKeyboxResult = Ok(rotatedKeybox(rotatedAuthKeys))
    accountAuthenticator.authResults = mutableListOf(
      Ok(authData(recoveryTokens)),
      Ok(authData(globalTokens))
    )

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")

    val authKeyRotationState = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = mockAccount.keybox,
      newKeyset = w3UpgradeKeyset()
    ).withProof(
      newAppAuthKeys = rotatedAuthKeys,
      proof = PrivilegedActionProof.HwKeyProof(mockProofOfPossession)
    ).withRotationData(
      hwSignedAccountId = "signed-account-id",
      hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
      appGlobalAuthKeyHwSignature = rotatedAuthKeys.appGlobalAuthKeyHwSignature
    )

    val result = service.proceed(state = authKeyRotationState)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.DescriptorBackup>()
    state.currentKeybox.shouldBe(rotatedKeybox(rotatedAuthKeys))
    state.proof.shouldBe(null)

    rotateAuthKeysF8eClient.rotateKeysetCalls.awaitItem()
    rotateAuthKeysF8eClient.lastRotateKeysetArgs.shouldNotBeNull().apply {
      oldAppAuthPublicKey.shouldBe(mockAccount.keybox.activeAppKeyBundle.authKey)
      newAppAuthPublicKeys.shouldBe(rotatedAuthKeys)
      hwAuthPublicKey.shouldBe(HwAuthSecp256k1PublicKeyMock)
      proof.shouldBe(
        PrivilegedActionProof.HwKeyProof(mockProofOfPossession)
      )
    }

    accountAuthenticator.authCalls.awaitItem().shouldBe(rotatedAuthKeys.appRecoveryAuthPublicKey)
    accountAuthenticator.authCalls.awaitItem().shouldBe(rotatedAuthKeys.appGlobalAuthPublicKey)
    accountAuthenticator.authCalls.expectNoEvents()
    keyboxDao.rotateAuthKeysCalls.awaitItem()
    keyboxDao.lastNewHwAuthPublicKey.shouldBe(HwAuthSecp256k1PublicKeyMock)

    authTokensService.getTokens(mockAccount.accountId, Global).get().shouldBe(globalTokens)
    authTokensService.getTokens(mockAccount.accountId, Recovery).get().shouldBe(recoveryTokens)

    // Verify device token was re-registered after auth key rotation
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()

    // Verify trusted contact endorsements were regenerated with correct keys and synced
    val endorseArgs = endorseTrustedContactsService.lastRegenerateAndEndorseArgs.shouldNotBeNull()
    endorseArgs.oldAppGlobalAuthKey.shouldBe(mockAccount.keybox.activeAppKeyBundle.authKey)
    endorseArgs.oldHwAuthKey.shouldBe(mockAccount.keybox.activeHwKeyBundle.authKey)
    endorseArgs.newAppGlobalAuthKey.shouldBe(rotatedAuthKeys.appGlobalAuthPublicKey)
    endorseArgs.newHwAuthKey.shouldBe(HwAuthSecp256k1PublicKeyMock)
    relationshipsService.syncCalls.awaitItem()

    val daoState = w3UpgradeDao.state.value.get().shouldNotBeNull()
    daoState.authKeyRotationCompleted.shouldBe(true)
  }

  test("W3Upgrade proceed from DescriptorBackup uses W3 action proof and returns keyset activation") {
    // Add a local-only keyset that won't be in backup results to exercise keyset preservation
    val localOnlyKeyset = SpendingKeysetMock.copy(
      localId = "local-only-keyset-id",
      f8eSpendingKeyset = F8eSpendingKeysetMock.copy(keysetId = "local-only-f8e-keyset-id")
    )
    val keyboxWithLocalKeyset = rotatedKeybox().copy(
      keysets = rotatedKeybox().keysets + localOnlyKeyset
    )
    accountService.setActiveAccount(mockAccount.copy(keybox = keyboxWithLocalKeyset))

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")
    w3UpgradeDao.setAuthKeyRotationComplete()
    onboardingKeyboxSealedSsekDao.set(mockSealedSsek)
    val uploadedKeysets = (rotatedKeybox().keysets + w3UpgradeKeyset())
      .distinctBy { it.f8eSpendingKeyset.keysetId }
    descriptorBackupService.uploadDescriptorBackupsResult = Ok(uploadedKeysets)
    val expectedKeybox = keyboxWithLocalKeyset.copy(
      activeSpendingKeyset = w3UpgradeKeyset(),
      keysets = listOf(rotatedKeybox().activeSpendingKeyset, w3UpgradeKeyset()),
      canUseKeyboxKeysets = true
    )

    val descriptorBackupState = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = keyboxWithLocalKeyset,
      newKeyset = w3UpgradeKeyset()
    ).withProof(
      descriptorBackupsProof
    )

    val result = service.proceed(state = descriptorBackupState)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.ServerKeysetActivation>()
    state.currentKeybox.shouldBe(expectedKeybox)
    state.proof.shouldBe(null)

    val uploadArgs = descriptorBackupService.lastUploadDescriptorBackupsArgs.shouldNotBeNull()
    uploadArgs.proof.shouldBe(descriptorBackupsProof)
    // W3Upgrade uses encrypt-only: no decryption of existing backups
    uploadArgs.sealedSsekForDecryption.shouldBe(null)
    uploadArgs.descriptorsToDecrypt.shouldBe(emptyList())
    // All keysets should be re-encrypted, with no duplicates
    val keysetIds = uploadArgs.keysetsToEncrypt.map { it.f8eSpendingKeyset.keysetId }
    keysetIds.shouldBe(keysetIds.distinct())

    val daoState = w3UpgradeDao.state.value.get().shouldNotBeNull()
    daoState.descriptorBackupCompleted.shouldBe(true)
    daoState.serverKeysetActivated.shouldBe(false)
    onboardingKeyboxSealedSsekDao.get().get().shouldBe(mockSealedSsek)
    keyboxDao.activeKeybox.value.get().shouldBe(expectedKeybox)
  }

  test("W3Upgrade descriptor backup keeps sealed SSEK when persisting completion fails") {
    accountService.setActiveAccount(rotatedAccount())

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")
    w3UpgradeDao.setAuthKeyRotationComplete()
    onboardingKeyboxSealedSsekDao.set(mockSealedSsek)
    w3UpgradeDao.shouldFailSetDescriptorBackupComplete = true
    descriptorBackupService.uploadDescriptorBackupsResult = Ok(
      (rotatedKeybox().keysets + w3UpgradeKeyset()).distinctBy { it.f8eSpendingKeyset.keysetId }
    )

    val descriptorBackupState = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    ).withProof(
      descriptorBackupsProof
    )

    val result = service.proceed(state = descriptorBackupState)

    result.shouldBeErrOfType<MigrationError.StatePersistenceFailed>()
    onboardingKeyboxSealedSsekDao.get().get().shouldBe(mockSealedSsek)
  }

  test("W3Upgrade descriptor backup returns missing context when sealed SSEK is unavailable") {
    accountService.setActiveAccount(rotatedAccount())

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")
    w3UpgradeDao.setAuthKeyRotationComplete()

    val descriptorBackupState = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    ).withProof(
      descriptorBackupsProof
    )

    val result = service.proceed(state = descriptorBackupState)

    result.shouldBeErrOfType<MigrationError.MissingContext>()
  }

  test("W3Upgrade can replay descriptor backup after completion while awaiting server keyset activation") {
    accountService.setActiveAccount(rotatedAccount())

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")
    w3UpgradeDao.setAuthKeyRotationComplete()
    onboardingKeyboxSealedSsekDao.set(mockSealedSsek)
    descriptorBackupService.uploadDescriptorBackupsResult = Ok(
      (rotatedKeybox().keysets + w3UpgradeKeyset()).distinctBy { it.f8eSpendingKeyset.keysetId }
    )

    val descriptorBackupState = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    ).withProof(
      descriptorBackupsProof
    )

    service.proceed(state = descriptorBackupState).shouldBeOk()
    onboardingKeyboxSealedSsekDao.get().get().shouldBe(mockSealedSsek)

    val replayResult = service.proceed(state = descriptorBackupState)

    replayResult.shouldBeOk()
    replayResult.get().shouldNotBeNull().shouldBeInstanceOf<MigrationProgress.ServerKeysetActivation>()
    descriptorBackupService.lastUploadDescriptorBackupsArgs.shouldNotBeNull()
      .sealedSsekForEncryption.shouldBe(mockSealedSsek)
  }

  test("W3Upgrade resumed-from-cloud descriptor backup repairs keybox and clears old wrapped SSEK") {
    val resumedNewKeyset = w3UpgradeKeyset().copy(
      f8eSpendingKeyset = F8eSpendingKeysetMock.copy(keysetId = "new-w3-keyset-id")
    )
    val recoveredHistoricalKeyset = createFakeSpendingKeyset(
      keysetId = "historical-keyset-id",
      localId = "historical-local-id"
    )
    val repairedKeysets = (rotatedKeybox().keysets + recoveredHistoricalKeyset + resumedNewKeyset)
      .distinctBy { it.f8eSpendingKeyset.keysetId }

    accountService.setActiveAccount(rotatedAccount())
    descriptorBackupService.uploadDescriptorBackupsResult = Ok(repairedKeysets)
    listKeysetsClient.result = Ok(
      ListKeysetsResponse(
        keysets = emptyList(),
        wrappedSsek = SealedSsekFake,
        descriptorBackups = listOf(
          DescriptorBackup(
            keysetId = recoveredHistoricalKeyset.f8eSpendingKeyset.keysetId,
            sealedDescriptor = XCiphertext("historical-descriptor"),
            privateWalletRootXpub = null
          )
        ),
        activeKeysetId = resumedNewKeyset.f8eSpendingKeyset.keysetId
      )
    )

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(resumedNewKeyset.f8eSpendingKeyset)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")
    w3UpgradeDao.setAuthKeyRotationComplete()
    w3UpgradeDao.setSealedSsekForDecryption(SealedSsekFake)
    // Simulate the SSEK that was persisted into ssekDao during the original pairing
    ssekDao.set(mockSealedSsek, SsekFake)

    val descriptorBackupState = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = resumedNewKeyset,
      sealedSsek = mockSealedSsek,
      resumedFromCloudBackup = true,
      sealedSsekForDecryption = SealedSsekFake
    ).withProof(
      descriptorBackupsProof
    )

    val result = service.proceed(descriptorBackupState)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull().shouldBeInstanceOf<MigrationProgress.ServerKeysetActivation>()
    state.currentKeybox.activeSpendingKeyset.shouldBe(resumedNewKeyset)
    state.currentKeybox.keysets.shouldBe(repairedKeysets)
    state.currentKeybox.canUseKeyboxKeysets.shouldBe(true)

    val uploadArgs = descriptorBackupService.lastUploadDescriptorBackupsArgs.shouldNotBeNull()
    uploadArgs.sealedSsekForDecryption.shouldBe(SealedSsekFake)
    uploadArgs.descriptorsToDecrypt.map { it.keysetId }.shouldBe(
      listOf(recoveredHistoricalKeyset.f8eSpendingKeyset.keysetId)
    )
    uploadArgs.keysetsToEncrypt.map { it.f8eSpendingKeyset.keysetId }
      .shouldBe(uploadArgs.keysetsToEncrypt.map { it.f8eSpendingKeyset.keysetId }.distinct())
    uploadArgs.keysetsToEncrypt.map { it.f8eSpendingKeyset.keysetId }
      .shouldBe(uploadArgs.keysetsToEncrypt.map { it.f8eSpendingKeyset.keysetId }
        .filter { it != recoveredHistoricalKeyset.f8eSpendingKeyset.keysetId })

    val daoState = w3UpgradeDao.state.value.get().shouldNotBeNull()
    daoState.sealedSsekForDecryption.shouldBe(null)

    // Verify the SSEK from pairing is still persisted in ssekDao
    ssekDao.get(mockSealedSsek).get().shouldBe(SsekFake)
  }

  test("W3Upgrade resumed-from-cloud descriptor backup keeps old wrapped SSEK if keybox repair fails") {
    val resumedNewKeyset = w3UpgradeKeyset().copy(
      f8eSpendingKeyset = F8eSpendingKeysetMock.copy(keysetId = "new-w3-keyset-id")
    )
    val recoveredHistoricalKeyset = createFakeSpendingKeyset(
      keysetId = "historical-keyset-id",
      localId = "historical-local-id"
    )

    accountService.setActiveAccount(rotatedAccount())
    descriptorBackupService.uploadDescriptorBackupsResult = Ok(
      (rotatedKeybox().keysets + recoveredHistoricalKeyset + resumedNewKeyset)
        .distinctBy { it.f8eSpendingKeyset.keysetId }
    )
    listKeysetsClient.result = Ok(
      ListKeysetsResponse(
        keysets = emptyList(),
        wrappedSsek = SealedSsekFake,
        descriptorBackups = listOf(
          DescriptorBackup(
            keysetId = recoveredHistoricalKeyset.f8eSpendingKeyset.keysetId,
            sealedDescriptor = XCiphertext("historical-descriptor"),
            privateWalletRootXpub = null
          )
        ),
        activeKeysetId = resumedNewKeyset.f8eSpendingKeyset.keysetId
      )
    )

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(resumedNewKeyset.f8eSpendingKeyset)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")
    w3UpgradeDao.setAuthKeyRotationComplete()
    w3UpgradeDao.setSealedSsekForDecryption(SealedSsekFake)
    keyboxDao.saveKeyboxAsActiveResult = Err(Error("failed to save repaired keybox"))

    val descriptorBackupState = MigrationProgress.DescriptorBackup(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = resumedNewKeyset,
      sealedSsek = mockSealedSsek,
      resumedFromCloudBackup = true,
      sealedSsekForDecryption = SealedSsekFake
    ).withProof(
      descriptorBackupsProof
    )

    val result = service.proceed(descriptorBackupState)

    result.shouldBeErrOfType<MigrationError.LocalKeyboxActivationFailed>()
    val daoState = w3UpgradeDao.state.value.get().shouldNotBeNull()
    daoState.sealedSsekForDecryption.shouldBe(SealedSsekFake)
  }

  test("W3Upgrade proceed from ServerKeysetActivation uses W3 action proof and returns provisioning step") {
    accountService.setActiveAccount(rotatedAccount())
    setActiveSpendingKeysetF8eClient.setResult = Ok(SignedKeysetVerificationResponseMock)

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")
    w3UpgradeDao.setAuthKeyRotationComplete()
    w3UpgradeDao.setDescriptorBackupComplete()
    onboardingKeyboxSealedSsekDao.set(mockSealedSsek)

    val serverKeysetActivationState = MigrationProgress.ServerKeysetActivation(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset()
    ).withProof(
      activateKeysetProof
    )

    val result = service.proceed(state = serverKeysetActivationState)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull().shouldBeInstanceOf<MigrationProgress.HardwareDescriptorProvisioning>()
    state.currentKeybox.shouldBe(rotatedKeybox())
    state.signedKeysResponse.shouldBe(SignedKeysetVerificationResponseMock)

    descriptorBackupService.lastUploadDescriptorBackupsArgs.shouldBe(null)
    setActiveSpendingKeysetF8eClient.lastSetArguments.shouldNotBeNull().proof.shouldBe(
      activateKeysetProof
    )
    // SSEK is preserved through ServerKeysetActivation — cleared only after cloud backup completes,
    // because earlier resume states rewind through DescriptorBackup which needs the SSEK.
    onboardingKeyboxSealedSsekDao.get().get().shouldBe(mockSealedSsek)
  }

  test("W3Upgrade proceed from HardwareDescriptorProvisioning stores signature and activates server keyset") {
    val provisioningSignature = AppGlobalAuthKeyHwSignature("provisioned-hw-signature")
    accountService.setActiveAccount(rotatedAccount())

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")
    w3UpgradeDao.setAuthKeyRotationComplete()
    w3UpgradeDao.setDescriptorBackupComplete()

    val hardwareDescriptorProvisioningState = MigrationProgress.HardwareDescriptorProvisioning(
      type = MigrationType.W3Upgrade,
      currentKeybox = rotatedKeybox(),
      newKeyset = w3UpgradeKeyset(),
      signedKeysResponse = SignedKeysetVerificationResponseMock
    ).withSignature(
      provisioningSignature
    )

    val result = service.proceed(state = hardwareDescriptorProvisioningState)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull().shouldBeInstanceOf<MigrationProgress.DdkBackup>()
    state.currentKeybox.activeAppKeyBundle.authKey.shouldBe(AppAuthPublicKeysMock.appGlobalAuthPublicKey)
    state.currentKeybox.appGlobalAuthKeyHwSignature.shouldBe(provisioningSignature)

    val daoState = w3UpgradeDao.state.value.get().shouldNotBeNull()
    daoState.serverKeysetActivated.shouldBe(true)
    keyboxDao.activeKeybox.value.get().shouldNotBeNull()
      .appGlobalAuthKeyHwSignature.shouldBe(provisioningSignature)
  }

  test("W3Upgrade CloudBackup clears onboarding sealed SSEK") {
    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    onboardingKeyboxSealedSsekDao.set(mockSealedSsek)

    val cloudBackupResult = service.proceed(
      MigrationProgress.CloudBackup(
        type = MigrationType.W3Upgrade,
        currentKeybox = rotatedKeybox(),
        newKeyset = w3UpgradeKeyset()
      )
    )

    cloudBackupResult.shouldBeOk()
    cloudBackupResult.get().shouldNotBeNull()
      .shouldBeInstanceOf<MigrationProgress.LocalKeyboxActivation>()
    onboardingKeyboxSealedSsekDao.get().get().shouldBe(null)
    w3UpgradeDao.state.value.get().shouldNotBeNull().cloudBackupCompleted.shouldBe(true)
  }

  test("getOldHardwareFingerprint returns persisted W3 checkpoint fingerprint") {
    service.getOldHardwareFingerprint().shouldBeOk(null)

    w3UpgradeDao.saveOldHardwareFingerprint("old-w1-fingerprint").shouldBeOk()

    service.getOldHardwareFingerprint().shouldBeOk("old-w1-fingerprint")
  }

  // -- Crash-safety: Issue 1 --

  test("W3Upgrade AuthKeyRotation persists pending auth rotation data before server call") {
    val rotatedAuthKeys = w3RotatedAuthKeys()
    accountService.setActiveAccount(mockAccount)
    keyboxDao.rotateKeyboxResult = Ok(rotatedKeybox(rotatedAuthKeys))

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")

    val authKeyRotationState = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = mockAccount.keybox,
      newKeyset = w3UpgradeKeyset()
    ).withProof(
      newAppAuthKeys = rotatedAuthKeys,
      proof = PrivilegedActionProof.HwKeyProof(mockProofOfPossession)
    ).withRotationData(
      hwSignedAccountId = "signed-account-id",
      hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
      appGlobalAuthKeyHwSignature = rotatedAuthKeys.appGlobalAuthKeyHwSignature
    )

    service.proceed(state = authKeyRotationState).shouldBeOk()
    rotateAuthKeysF8eClient.rotateKeysetCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem().shouldBe(rotatedAuthKeys.appRecoveryAuthPublicKey)
    accountAuthenticator.authCalls.awaitItem().shouldBe(rotatedAuthKeys.appGlobalAuthPublicKey)
    accountAuthenticator.authCalls.expectNoEvents()
    keyboxDao.rotateAuthKeysCalls.awaitItem()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
    relationshipsService.syncCalls.awaitItem()

    // After successful completion, pending data should be cleared
    val daoState = w3UpgradeDao.state.value.get().shouldNotBeNull()
    daoState.pendingAppGlobalAuthKey.shouldBe(null)
    daoState.pendingAppRecoveryAuthKey.shouldBe(null)
    daoState.pendingHwAuthPublicKey.shouldBe(null)
    daoState.pendingHwSignedAccountId.shouldBe(null)
    daoState.serverAuthRotationCompleted.shouldBe(false)
  }

  test("W3Upgrade resume reconstructs AuthKeyRotation from persisted pending data after crash") {
    val rotatedAuthKeys = w3RotatedAuthKeys()
    accountService.setActiveAccount(mockAccount)

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")
    // Simulate crash: pending data was persisted but auth rotation did not complete
    w3UpgradeDao.savePendingAuthRotationData(
      newAppAuthKeys = rotatedAuthKeys,
      hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
      hwSignedAccountId = "signed-account-id",
      oldAppGlobalAuthKey = mockAccount.keybox.activeAppKeyBundle.authKey,
      oldHwAuthPublicKey = mockAccount.keybox.activeHwKeyBundle.authKey
    )

    val result = service.resume(MigrationType.W3Upgrade)

    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.AuthKeyRotation>()
    // Should be pre-populated with the persisted auth rotation data
    state.newAppAuthKeys.shouldNotBeNull()
    state.newAppAuthKeys!!.appGlobalAuthPublicKey.shouldBe(rotatedAuthKeys.appGlobalAuthPublicKey)
    state.newAppAuthKeys!!.appRecoveryAuthPublicKey.shouldBe(rotatedAuthKeys.appRecoveryAuthPublicKey)
    state.hwAuthPublicKey.shouldBe(HwAuthSecp256k1PublicKeyMock)
    state.hwSignedAccountId.shouldBe("signed-account-id")
    // proof is intentionally null — the caller must supply a real proof
    // if the server call still needs to happen.
    state.proof.shouldBe(null)
  }

  test("W3Upgrade auth rotation returns missing context when only old W1 proof is missing") {
    val rotatedAuthKeys = w3RotatedAuthKeys()
    accountService.setActiveAccount(mockAccount)

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")
    w3UpgradeDao.savePendingAuthRotationData(
      newAppAuthKeys = rotatedAuthKeys,
      hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
      hwSignedAccountId = "signed-account-id",
      oldAppGlobalAuthKey = mockAccount.keybox.activeAppKeyBundle.authKey,
      oldHwAuthPublicKey = mockAccount.keybox.activeHwKeyBundle.authKey
    )

    val result = service.proceed(
      state = MigrationProgress.AuthKeyRotation(
        type = MigrationType.W3Upgrade,
        currentKeybox = mockAccount.keybox,
        newKeyset = w3UpgradeKeyset(),
        newAppAuthKeys = rotatedAuthKeys,
        hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
        hwSignedAccountId = "signed-account-id"
      )
    )

    result.shouldBeErrOfType<MigrationError.MissingContext.W3AuthRotationOldHardwareProof>()
    accountAuthenticator.authCalls.expectNoEvents()
  }

  test("W3Upgrade resume with serverAuthRotationCompleted uses persisted keys, not state keys") {
    val rotatedAuthKeys = w3RotatedAuthKeys()
    accountService.setActiveAccount(mockAccount)
    keyboxDao.rotateKeyboxResult = Ok(rotatedKeybox(rotatedAuthKeys))

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")
    // Simulate crash AFTER server rotation succeeded with the original keys
    w3UpgradeDao.savePendingAuthRotationData(
      newAppAuthKeys = rotatedAuthKeys,
      hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
      hwSignedAccountId = "signed-account-id",
      oldAppGlobalAuthKey = mockAccount.keybox.activeAppKeyBundle.authKey,
      oldHwAuthPublicKey = mockAccount.keybox.activeHwKeyBundle.authKey
    )
    w3UpgradeDao.setServerAuthRotationCompleted()

    // Make the server rotate call fail to prove it's not called
    rotateAuthKeysF8eClient.rotateKeysetResult =
      Err(build.wallet.ktor.result.HttpError.UnhandledException(RuntimeException("should not be called")))

    // Build a state with DIFFERENT keys than what was persisted — simulating
    // the UI generating a different recovery key via GeneratingAuthKeys on resume.
    val differentAppAuthKeys = AppAuthPublicKeys(
      appGlobalAuthPublicKey = mockAccount.keybox.activeAppKeyBundle.authKey,
      appRecoveryAuthPublicKey = PublicKey<AppRecoveryAuthKey>("different-app-recovery-auth-key"),
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature("different-hw-sig")
    )
    val stateWithDifferentKeys = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = mockAccount.keybox,
      newKeyset = w3UpgradeKeyset()
    ).withProof(
      newAppAuthKeys = differentAppAuthKeys,
      proof = PrivilegedActionProof.HwKeyProof(mockProofOfPossession)
    ).withRotationData(
      hwSignedAccountId = "different-signed-id",
      hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
      appGlobalAuthKeyHwSignature = differentAppAuthKeys.appGlobalAuthKeyHwSignature
    )

    // Proceed should skip the server call and use the PERSISTED keys
    val result = service.proceed(state = stateWithDifferentKeys)
    result.shouldBeOk()
    val nextState = result.get().shouldNotBeNull()
    nextState.shouldBeInstanceOf<MigrationProgress.DescriptorBackup>()
    nextState.currentKeybox.shouldBe(rotatedKeybox(rotatedAuthKeys))

    // Verify local rotation used the persisted keys,
    // not the different keys from the state.
    keyboxDao.rotateAuthKeysCalls.awaitItem()
    keyboxDao.lastNewHwAuthPublicKey.shouldBe(HwAuthSecp256k1PublicKeyMock)

    accountAuthenticator.authCalls.awaitItem().shouldBe(rotatedAuthKeys.appRecoveryAuthPublicKey)
    accountAuthenticator.authCalls.awaitItem().shouldBe(rotatedAuthKeys.appGlobalAuthPublicKey)
    accountAuthenticator.authCalls.expectNoEvents()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
    relationshipsService.syncCalls.awaitItem()
  }

  // -- Recovery token refresh after mixed auth rotation --

  test("W3Upgrade recovery token refresh failure preserves existing tokens") {
    val rotatedAuthKeys = w3RotatedAuthKeys()
    accountService.setActiveAccount(mockAccount)
    keyboxDao.rotateKeyboxResult = Ok(rotatedKeybox(rotatedAuthKeys))
    val recoveryTokens = AccountAuthTokens(
      accessToken = AccessToken("r-access"),
      refreshToken = RefreshToken("r-refresh"),
      accessTokenExpiresAt = Instant.DISTANT_FUTURE,
      refreshTokenExpiresAt = Instant.DISTANT_FUTURE
    )
    accountAuthenticator.authResults = mutableListOf(
      Ok(authData(recoveryTokens))
    )

    val oldGlobalTokens = AccountAuthTokens(
      accessToken = AccessToken("old-global-access"),
      refreshToken = RefreshToken("old-global-refresh"),
      accessTokenExpiresAt = Instant.DISTANT_FUTURE,
      refreshTokenExpiresAt = Instant.DISTANT_FUTURE
    )
    val oldRecoveryTokens = AccountAuthTokens(
      accessToken = AccessToken("old-recovery-access"),
      refreshToken = RefreshToken("old-recovery-refresh"),
      accessTokenExpiresAt = Instant.DISTANT_FUTURE,
      refreshTokenExpiresAt = Instant.DISTANT_FUTURE
    )
    authTokensService.setTokens(mockAccount.accountId, oldGlobalTokens, Global)
    authTokensService.setTokens(mockAccount.accountId, oldRecoveryTokens, Recovery)

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")

    authTokensService.setTokensErrorForScope[Recovery] =
      Error("Recovery token persist failed")

    val authKeyRotationState = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = mockAccount.keybox,
      newKeyset = w3UpgradeKeyset()
    ).withProof(
      newAppAuthKeys = rotatedAuthKeys,
      proof = PrivilegedActionProof.HwKeyProof(mockProofOfPossession)
    ).withRotationData(
      hwSignedAccountId = "signed-account-id",
      hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
      appGlobalAuthKeyHwSignature = rotatedAuthKeys.appGlobalAuthKeyHwSignature
    )

    val result = service.proceed(state = authKeyRotationState)

    result.shouldBeErrOfType<MigrationError.AuthKeyRotationFailed>()

    rotateAuthKeysF8eClient.rotateKeysetCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem().shouldBe(rotatedAuthKeys.appRecoveryAuthPublicKey)
    accountAuthenticator.authCalls.expectNoEvents()
    keyboxDao.rotateAuthKeysCalls.awaitItem()

    authTokensService.getTokens(mockAccount.accountId, Global).get().shouldBe(oldGlobalTokens)
    authTokensService.getTokens(mockAccount.accountId, Recovery).get().shouldBe(oldRecoveryTokens)
  }

  // -- Checkpoint before recovery token refresh --

  test("W3Upgrade recovery token refresh failure retries from AuthKeyRotation without re-calling server") {
    val rotatedAuthKeys = w3RotatedAuthKeys()
    accountService.setActiveAccount(mockAccount)
    keyboxDao.rotateKeyboxResult = Ok(rotatedKeybox(rotatedAuthKeys))

    accountAuthenticator.authResults = mutableListOf(
      Err(build.wallet.auth.AuthProtocolError("token refresh failed"))
    )

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")

    val authKeyRotationState = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = mockAccount.keybox,
      newKeyset = w3UpgradeKeyset()
    ).withProof(
      newAppAuthKeys = rotatedAuthKeys,
      proof = PrivilegedActionProof.HwKeyProof(mockProofOfPossession)
    ).withRotationData(
      hwSignedAccountId = "signed-account-id",
      hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
      appGlobalAuthKeyHwSignature = rotatedAuthKeys.appGlobalAuthKeyHwSignature
    )

    val result = service.proceed(state = authKeyRotationState)
    result.shouldBeErrOfType<MigrationError.AuthKeyRotationFailed>()

    rotateAuthKeysF8eClient.rotateKeysetCalls.awaitItem()
    accountAuthenticator.authCalls.awaitItem().shouldBe(rotatedAuthKeys.appRecoveryAuthPublicKey)
    accountAuthenticator.authCalls.expectNoEvents()
    keyboxDao.rotateAuthKeysCalls.awaitItem()

    val daoState = w3UpgradeDao.state.value.get().shouldNotBeNull()
    daoState.authKeyRotationCompleted.shouldBe(false)

    daoState.serverAuthRotationCompleted.shouldBe(true)

    accountService.setActiveAccount(rotatedAccount(rotatedAuthKeys))
    val resumeResult = service.resume(MigrationType.W3Upgrade)
    resumeResult.shouldBeOk()
    val resumeState = resumeResult.get().shouldNotBeNull()
    resumeState.shouldBeInstanceOf<MigrationProgress.AuthKeyRotation>()
    resumeState.newAppAuthKeys.shouldNotBeNull()
    resumeState.newAppAuthKeys!!.appGlobalAuthPublicKey
      .shouldBe(rotatedAuthKeys.appGlobalAuthPublicKey)
    resumeState.newAppAuthKeys!!.appRecoveryAuthPublicKey
      .shouldBe(rotatedAuthKeys.appRecoveryAuthPublicKey)
    resumeState.proof.shouldBe(null)
  }

  test("W3Upgrade retry after keybox already rotated uses persisted pre-rotation keys for TC endorsements") {
    val rotatedAuthKeys = w3RotatedAuthKeys()
    // Simulate: the keybox has already been rotated locally (previous attempt got
    // past rotateKeyboxAuthKeys but failed on token refresh or TC endorsements).
    // The active account now has the NEW auth keys.
    accountService.setActiveAccount(rotatedAccount(rotatedAuthKeys))
    keyboxDao.rotateKeyboxResult = Ok(rotatedKeybox(rotatedAuthKeys))

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")
    // Pending data includes the ORIGINAL pre-rotation keys
    w3UpgradeDao.savePendingAuthRotationData(
      newAppAuthKeys = rotatedAuthKeys,
      hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
      hwSignedAccountId = "signed-account-id",
      oldAppGlobalAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey,
      oldHwAuthPublicKey = FullAccountMock.keybox.activeHwKeyBundle.authKey
    )
    w3UpgradeDao.setServerAuthRotationCompleted()

    // Make server call fail to prove it's skipped
    rotateAuthKeysF8eClient.rotateKeysetResult =
      Err(build.wallet.ktor.result.HttpError.UnhandledException(RuntimeException("should not be called")))

    // Resume and proceed — should use persisted pre-rotation keys for TC endorsements
    val resumed = service.resume(MigrationType.W3Upgrade)
    resumed.shouldBeOk()
    val state = resumed.get().shouldNotBeNull()
      .shouldBeInstanceOf<MigrationProgress.AuthKeyRotation>()

    val result = service.proceed(state = state)
    result.shouldBeOk()

    accountAuthenticator.authCalls.awaitItem().shouldBe(rotatedAuthKeys.appRecoveryAuthPublicKey)
    accountAuthenticator.authCalls.awaitItem().shouldBe(rotatedAuthKeys.appGlobalAuthPublicKey)
    accountAuthenticator.authCalls.expectNoEvents()
    keyboxDao.rotateAuthKeysCalls.awaitItem()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
    relationshipsService.syncCalls.awaitItem()

    // Verify TC endorsements used the ORIGINAL (pre-rotation) keys, not the
    // already-rotated keys from the active account's keybox.
    val endorseArgs = endorseTrustedContactsService.lastRegenerateAndEndorseArgs.shouldNotBeNull()
    endorseArgs.oldAppGlobalAuthKey.shouldBe(FullAccountMock.keybox.activeAppKeyBundle.authKey)
    endorseArgs.oldHwAuthKey.shouldBe(FullAccountMock.keybox.activeHwKeyBundle.authKey)
    endorseArgs.newAppGlobalAuthKey.shouldBe(rotatedAuthKeys.appGlobalAuthPublicKey)
    endorseArgs.newHwAuthKey.shouldBe(HwAuthSecp256k1PublicKeyMock)
  }

  test("W3Upgrade crash after server rotation but before checkpoint still succeeds on retry") {
    val rotatedAuthKeys = w3RotatedAuthKeys()
    // Scenario: server rotation succeeded, but the app crashed before
    // setServerAuthRotationCompleted() was written. On retry, the
    // persisted pending keys are verified against the server BEFORE any
    // new server call.  Since they are already active, the flow
    // short-circuits and reuses them without calling rotateKeyset again.
    accountService.setActiveAccount(mockAccount)
    keyboxDao.rotateKeyboxResult = Ok(rotatedKeybox(rotatedAuthKeys))

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")
    // Pending data was persisted (before the server call), but the
    // serverAuthRotationCompleted checkpoint was NOT written (crash).
    w3UpgradeDao.savePendingAuthRotationData(
      newAppAuthKeys = rotatedAuthKeys,
      hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
      hwSignedAccountId = "signed-account-id",
      oldAppGlobalAuthKey = mockAccount.keybox.activeAppKeyBundle.authKey,
      oldHwAuthPublicKey = mockAccount.keybox.activeHwKeyBundle.authKey
    )
    // NOTE: serverAuthRotationCompleted is NOT set — simulating the crash window.

    authF8eClient.initiateAuthenticationResult = Ok(hwAuthInitiationSuccess())

    val authKeyRotationState = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = mockAccount.keybox,
      newKeyset = w3UpgradeKeyset()
    ).withProof(
      newAppAuthKeys = rotatedAuthKeys,
      proof = PrivilegedActionProof.HwKeyProof(mockProofOfPossession)
    ).withRotationData(
      hwSignedAccountId = "signed-account-id",
      hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
      appGlobalAuthKeyHwSignature = rotatedAuthKeys.appGlobalAuthKeyHwSignature
    )

    // Should succeed because persisted keys are detected as already active.
    val result = service.proceed(state = authKeyRotationState)
    result.shouldBeOk()
    val state = result.get().shouldNotBeNull()
    state.shouldBeInstanceOf<MigrationProgress.DescriptorBackup>()
    state.currentKeybox.shouldBe(rotatedKeybox(rotatedAuthKeys))

    accountAuthenticator.authCalls.awaitItem().shouldBe(rotatedAuthKeys.appRecoveryAuthPublicKey)
    accountAuthenticator.authCalls.awaitItem().shouldBe(rotatedAuthKeys.appGlobalAuthPublicKey)
    accountAuthenticator.authCalls.expectNoEvents()
    keyboxDao.rotateAuthKeysCalls.awaitItem()
    deviceTokenManager.addDeviceTokenIfPresentForAccountCalls.awaitItem()
    relationshipsService.syncCalls.awaitItem()

    // Checkpoint should now be persisted
    val daoState = w3UpgradeDao.state.value.get().shouldNotBeNull()
    daoState.authKeyRotationCompleted.shouldBe(true)
  }

  test("W3Upgrade transient error during prior-key check preserves persisted keys") {
    val rotatedAuthKeys = w3RotatedAuthKeys()
    // Scenario: persisted pending keys exist (no checkpoint), but the auth
    // check to verify them hits a transient network error.  The flow must
    // NOT overwrite the persisted keys and should propagate the error.
    accountService.setActiveAccount(mockAccount)

    w3UpgradeDao.saveHardwareKey(mockNewHwKeys.spendingKey)
    w3UpgradeDao.saveAppKey(AppKeyBundleMock.spendingKey)
    w3UpgradeDao.saveServerKey(F8eSpendingKeysetMock)
    w3UpgradeDao.saveKeysetLocalId("uuid-0")
    w3UpgradeDao.savePendingAuthRotationData(
      newAppAuthKeys = rotatedAuthKeys,
      hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
      hwSignedAccountId = "signed-account-id",
      oldAppGlobalAuthKey = mockAccount.keybox.activeAppKeyBundle.authKey,
      oldHwAuthPublicKey = mockAccount.keybox.activeHwKeyBundle.authKey
    )

    authF8eClient.initiateAuthenticationResult = Err(
      HttpError.NetworkError(Throwable("transient network failure"))
    )

    val authKeyRotationState = MigrationProgress.AuthKeyRotation(
      type = MigrationType.W3Upgrade,
      currentKeybox = mockAccount.keybox,
      newKeyset = w3UpgradeKeyset()
    ).withProof(
      newAppAuthKeys = rotatedAuthKeys,
      proof = PrivilegedActionProof.HwKeyProof(mockProofOfPossession)
    ).withRotationData(
      hwSignedAccountId = "signed-account-id",
      hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock,
      appGlobalAuthKeyHwSignature = rotatedAuthKeys.appGlobalAuthKeyHwSignature
    )

    val result = service.proceed(state = authKeyRotationState)
    result.shouldBeErrOfType<MigrationError.AuthKeyRotationFailed>()

    accountAuthenticator.authCalls.expectNoEvents()

    // Persisted pending keys must NOT have been overwritten.
    val entity = w3UpgradeDao.state.value.get().shouldNotBeNull()
    entity.pendingAppGlobalAuthKey.shouldBe(rotatedAuthKeys.appGlobalAuthPublicKey)
    entity.pendingAppRecoveryAuthKey.shouldBe(rotatedAuthKeys.appRecoveryAuthPublicKey)
    entity.serverAuthRotationCompleted.shouldBe(false)
  }
})
