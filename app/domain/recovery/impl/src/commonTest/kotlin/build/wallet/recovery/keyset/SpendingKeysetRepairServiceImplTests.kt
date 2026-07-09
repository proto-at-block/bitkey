@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.recovery.keyset

import app.cash.turbine.test
import bitkey.backup.DescriptorBackup
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.FullAccountW3Mock
import build.wallet.bitkey.keybox.PrivateWalletKeyboxMock
import build.wallet.bitkey.spending.HwSpendingPublicKeyMock
import build.wallet.f8e.auth.ActionProofHeader
import build.wallet.cloud.backup.CloudBackupServiceFake
import build.wallet.cloud.backup.CloudBackupV2WithFullAccountMock
import build.wallet.cloud.backup.FullAccountCloudBackupCreatorMock
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudStoreAccountRepositoryMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.onboarding.CreateAccountKeysetV2F8eClientFake
import build.wallet.f8e.onboarding.SetActiveSpendingKeysetF8eClientFake
import build.wallet.f8e.recovery.LegacyRemoteKeyset
import build.wallet.f8e.recovery.ListKeysetsF8eClientMock
import build.wallet.f8e.recovery.ListKeysetsResponse
import build.wallet.f8e.recovery.PrivateMultisigRemoteKeyset
import build.wallet.f8e.recovery.SignedKeysetVerificationResponseMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.KeysetRepairFeatureFlag
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.keybox.keys.AppKeysGeneratorMock
import build.wallet.ktor.result.HttpError
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.recovery.DescriptorBackupServiceFake
import build.wallet.recovery.createFakeLegacyRemoteKeyset
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.ExperimentalCoroutinesApi

// Large end-to-end coverage for keyset repair; splitting would hurt cohesion.
@Suppress("LargeClass")
class SpendingKeysetRepairServiceImplTests : FunSpec({
  val accountService = AccountServiceFake()
  val listKeysetsF8eClient = ListKeysetsF8eClientMock()
  val featureFlagDao = FeatureFlagDaoFake()
  val keysetRepairFeatureFlag = KeysetRepairFeatureFlag(featureFlagDao)
  val descriptorBackupService = DescriptorBackupServiceFake()
  val keyboxDao = KeyboxDaoMock(turbines::create)
  val fullAccountCloudBackupCreator = FullAccountCloudBackupCreatorMock(turbines::create)
  val cloudBackupService = CloudBackupServiceFake()
  val cloudStoreAccountRepository = CloudStoreAccountRepositoryMock()
  val uuidGenerator = UuidGeneratorFake()
  val appSessionManager = AppSessionManagerFake()
  val appKeysGenerator = AppKeysGeneratorMock()
  val createAccountKeysetV2F8eClient = CreateAccountKeysetV2F8eClientFake()
  val setActiveSpendingKeysetF8eClient = SetActiveSpendingKeysetF8eClientFake()
  val testCloudStoreAccount = CloudAccountMock("test")

  fun service() =
    SpendingKeysetRepairServiceImpl(
      accountService = accountService,
      listKeysetsF8eClient = listKeysetsF8eClient,
      keysetRepairFeatureFlag = keysetRepairFeatureFlag,
      descriptorBackupService = descriptorBackupService,
      keyboxDao = keyboxDao,
      fullAccountCloudBackupCreator = fullAccountCloudBackupCreator,
      cloudBackupService = cloudBackupService,
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      uuidGenerator = uuidGenerator,
      appKeysGenerator = appKeysGenerator,
      createAccountKeysetV2F8eClient = createAccountKeysetV2F8eClient,
      setActiveSpendingKeysetF8eClient = setActiveSpendingKeysetF8eClient,
      appSessionManager = appSessionManager
    )

  beforeTest {
    accountService.reset()
    listKeysetsF8eClient.reset()
    featureFlagDao.reset()
    descriptorBackupService.reset()
    keyboxDao.reset()
    fullAccountCloudBackupCreator.reset()
    cloudBackupService.reset()
    cloudStoreAccountRepository.reset()
    uuidGenerator.reset()
    appSessionManager.reset()
    appKeysGenerator.reset()
    createAccountKeysetV2F8eClient.reset()
    setActiveSpendingKeysetF8eClient.reset()

    // Enable feature flag by default
    keysetRepairFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
  }

  context("syncStatus") {
    test("returns Synced when no active account") {
      accountService.accountState.value = Ok(AccountStatus.NoAccount)

      val service = service()
      service.executeWork()

      service.syncStatus.value.shouldBe(SpendingKeysetSyncStatus.Synced)
    }

    test("returns Synced when local and server keyset IDs match") {
      accountService.setActiveAccount(FullAccountMock)
      val localKeysetId = FullAccountMock.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
      listKeysetsF8eClient.result = Ok(
        ListKeysetsResponse(
          keysets = listOf(createFakeLegacyRemoteKeyset(localKeysetId)),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = localKeysetId
        )
      )

      val service = service()
      service.executeWork()

      service.syncStatus.value.shouldBe(SpendingKeysetSyncStatus.Synced)
    }

    test("returns IncompleteKeysetList when server has keysets missing locally") {
      accountService.setActiveAccount(FullAccountMock)
      val localKeysetId = FullAccountMock.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
      val missingKeysetId = "missing-server-keyset-id"
      listKeysetsF8eClient.result = Ok(
        ListKeysetsResponse(
          keysets = listOf(
            createFakeLegacyRemoteKeyset(localKeysetId),
            createFakeLegacyRemoteKeyset(missingKeysetId)
          ),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = localKeysetId
        )
      )

      val service = service()
      service.executeWork()

      val status = service.syncStatus.value
      status.shouldBeInstanceOf<SpendingKeysetSyncStatus.IncompleteKeysetList>()
      status.activeKeysetId.shouldBe(localKeysetId)
      status.missingKeysetIds.shouldBe(setOf(missingKeysetId))
    }

    test("returns IncompleteKeysetList when private wallet server has inactive legacy keysets missing locally") {
      val privateAccount = FullAccountMock.copy(
        keybox = PrivateWalletKeyboxMock.copy(
          keysets = listOf(PrivateWalletKeyboxMock.activeSpendingKeyset),
          canUseKeyboxKeysets = true
        )
      )
      accountService.setActiveAccount(privateAccount)
      val activePrivateKeysetId =
        privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
      val missingKeysetId = "missing-legacy-keyset-id"
      listKeysetsF8eClient.result = Ok(
        ListKeysetsResponse(
          keysets = listOf(
            createFakeLegacyRemoteKeyset(missingKeysetId),
            PrivateMultisigRemoteKeyset(
              keysetId = activePrivateKeysetId,
              networkType = "SIGNET",
              appPublicKey = privateAccount.keybox.activeSpendingKeyset.appKey.key.dpub,
              hardwarePublicKey = privateAccount.keybox.activeSpendingKeyset.hardwareKey.key.dpub,
              serverPublicKey =
                privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
            )
          ),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = activePrivateKeysetId
        )
      )

      val service = service()
      service.executeWork()

      val status = service.syncStatus.value
      status.shouldBeInstanceOf<SpendingKeysetSyncStatus.IncompleteKeysetList>()
      status.activeKeysetId.shouldBe(activePrivateKeysetId)
      status.missingKeysetIds.shouldBe(setOf(missingKeysetId))
    }

    test("returns Synced when server has keysets missing locally but local keysets are not authoritative") {
      val nonAuthoritativeAccount = FullAccountMock.copy(
        keybox = FullAccountMock.keybox.copy(canUseKeyboxKeysets = false)
      )
      accountService.setActiveAccount(nonAuthoritativeAccount)
      val localKeysetId =
        nonAuthoritativeAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
      val missingKeysetId = "missing-server-keyset-id"
      listKeysetsF8eClient.result = Ok(
        ListKeysetsResponse(
          keysets = listOf(
            createFakeLegacyRemoteKeyset(localKeysetId),
            createFakeLegacyRemoteKeyset(missingKeysetId)
          ),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = localKeysetId
        )
      )

      val service = service()
      service.executeWork()

      service.syncStatus.value.shouldBe(SpendingKeysetSyncStatus.Synced)
    }

    test("returns Mismatch when local and server keyset IDs differ and feature flag enabled") {
      accountService.setActiveAccount(FullAccountMock)
      val localKeysetId = FullAccountMock.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
      val serverKeysetId = "different-server-keyset-id"
      listKeysetsF8eClient.activeKeysetId = serverKeysetId

      val service = service()
      service.executeWork()

      val status = service.syncStatus.value
      status.shouldBeInstanceOf<SpendingKeysetSyncStatus.Mismatch>()
      status.localActiveKeysetId.shouldBe(localKeysetId)
      status.serverActiveKeysetId.shouldBe(serverKeysetId)
    }

    test("returns Synced when mismatch exists but feature flag disabled") {
      keysetRepairFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
      accountService.setActiveAccount(FullAccountMock)
      listKeysetsF8eClient.activeKeysetId = "different-server-keyset-id"

      val service = service()
      service.executeWork()

      service.syncStatus.value.shouldBe(SpendingKeysetSyncStatus.Synced)
    }

    test("returns Unknown when network request fails") {
      accountService.setActiveAccount(FullAccountMock)
      val networkError = HttpError.NetworkError(RuntimeException("Network unavailable"))
      listKeysetsF8eClient.result = Err(networkError)

      val service = service()
      service.executeWork()

      val status = service.syncStatus.value
      status.shouldBeInstanceOf<SpendingKeysetSyncStatus.Unknown>()
    }

    test("returns IncompletePrivateWallet when private wallet keybox is not authoritative") {
      val privateAccount = FullAccountMock.copy(
        keybox = PrivateWalletKeyboxMock.copy(
          keysets = listOf(PrivateWalletKeyboxMock.activeSpendingKeyset),
          canUseKeyboxKeysets = false
        )
      )
      accountService.setActiveAccount(privateAccount)
      listKeysetsF8eClient.result = Ok(
        ListKeysetsResponse(
          keysets = listOf(
            createFakeLegacyRemoteKeyset("legacy-keyset-1"),
            PrivateMultisigRemoteKeyset(
              keysetId = privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId,
              networkType = "SIGNET",
              appPublicKey = privateAccount.keybox.activeSpendingKeyset.appKey.key.dpub,
              hardwarePublicKey = privateAccount.keybox.activeSpendingKeyset.hardwareKey.key.dpub,
              serverPublicKey =
                privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
            )
          ),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
        )
      )

      val service = service()
      service.executeWork()

      val status = service.syncStatus.value
      status.shouldBeInstanceOf<SpendingKeysetSyncStatus.IncompletePrivateWallet>()
      status.activeKeysetId.shouldBe(
        privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
      )
    }
  }

  context("checkPrivateKeysets") {
    test("returns None when no private keysets exist") {
      accountService.setActiveAccount(FullAccountMock)
      listKeysetsF8eClient.result = Ok(
        ListKeysetsResponse(
          keysets = listOf(
            LegacyRemoteKeyset(
              keysetId = "legacy-keyset-1",
              networkType = "SIGNET",
              appDescriptor = DescriptorPublicKeyMock(identifier = "app-1").dpub,
              hardwareDescriptor = DescriptorPublicKeyMock(identifier = "hw-1").dpub,
              serverDescriptor = DescriptorPublicKeyMock(identifier = "server-1").dpub
            )
          ),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = "legacy-keyset-1"
        )
      )

      val result = service().checkPrivateKeysets(FullAccountMock)

      val info = result.shouldBeOk()
      info.shouldBeInstanceOf<PrivateKeysetInfo.None>()
      info.cachedResponseData.serverActiveKeysetId.shouldBe("legacy-keyset-1")
    }

    test("returns NeedsUnsealing when descriptor backups exist with wrapped SSEK") {
      accountService.setActiveAccount(FullAccountMock)

      val descriptorBackup = DescriptorBackup(
        keysetId = "private-keyset-1",
        sealedDescriptor = XCiphertext("fake-sealed-descriptor"),
        privateWalletRootXpub = XCiphertext("fake-private-wallet-root-xpub")
      )

      listKeysetsF8eClient.result = Ok(
        ListKeysetsResponse(
          keysets = listOf(
            PrivateMultisigRemoteKeyset(
              keysetId = "private-keyset-1",
              networkType = "SIGNET",
              appPublicKey = "app-pub-key",
              hardwarePublicKey = "hw-pub-key",
              serverPublicKey = "server-pub-key"
            )
          ),
          wrappedSsek = SealedSsekFake,
          descriptorBackups = listOf(descriptorBackup),
          activeKeysetId = "private-keyset-1"
        )
      )

      val result = service().checkPrivateKeysets(FullAccountMock)

      val info = result.shouldBeOk()
      info.shouldBeInstanceOf<PrivateKeysetInfo.NeedsUnsealing>()
      info.cachedResponseData.serverActiveKeysetId.shouldBe("private-keyset-1")
    }

    test("returns None when only the active private keyset needs to be reconstructed") {
      val privateAccount = FullAccountMock.copy(
        keybox = PrivateWalletKeyboxMock.copy(
          keysets = listOf(PrivateWalletKeyboxMock.activeSpendingKeyset),
          canUseKeyboxKeysets = false
        )
      )
      accountService.setActiveAccount(privateAccount)

      val activeKeysetId = privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
      listKeysetsF8eClient.result = Ok(
        ListKeysetsResponse(
          keysets = listOf(
            createFakeLegacyRemoteKeyset("legacy-keyset-1"),
            PrivateMultisigRemoteKeyset(
              keysetId = activeKeysetId,
              networkType = "SIGNET",
              appPublicKey = privateAccount.keybox.activeSpendingKeyset.appKey.key.dpub,
              hardwarePublicKey = privateAccount.keybox.activeSpendingKeyset.hardwareKey.key.dpub,
              serverPublicKey =
                privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
            )
          ),
          wrappedSsek = SealedSsekFake,
          descriptorBackups = listOf(
            DescriptorBackup(
              keysetId = activeKeysetId,
              sealedDescriptor = XCiphertext("fake-sealed-descriptor"),
              privateWalletRootXpub = XCiphertext("fake-private-wallet-root-xpub")
            )
          ),
          activeKeysetId = activeKeysetId
        )
      )

      val result = service().checkPrivateKeysets(privateAccount)

      val info = result.shouldBeOk()
      info.shouldBeInstanceOf<PrivateKeysetInfo.None>()
      info.cachedResponseData.serverActiveKeysetId.shouldBe(activeKeysetId)
    }

    test("returns NeedsUnsealing when multiple private keysets exist") {
      val privateAccount = FullAccountMock.copy(
        keybox = PrivateWalletKeyboxMock.copy(
          keysets = listOf(PrivateWalletKeyboxMock.activeSpendingKeyset),
          canUseKeyboxKeysets = false
        )
      )
      accountService.setActiveAccount(privateAccount)

      listKeysetsF8eClient.result = Ok(
        ListKeysetsResponse(
          keysets = listOf(
            PrivateMultisigRemoteKeyset(
              keysetId = privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId,
              networkType = "SIGNET",
              appPublicKey = privateAccount.keybox.activeSpendingKeyset.appKey.key.dpub,
              hardwarePublicKey = privateAccount.keybox.activeSpendingKeyset.hardwareKey.key.dpub,
              serverPublicKey =
                privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
            ),
            PrivateMultisigRemoteKeyset(
              keysetId = "private-keyset-2",
              networkType = "SIGNET",
              appPublicKey = DescriptorPublicKeyMock(identifier = "private-app-2").dpub,
              hardwarePublicKey = DescriptorPublicKeyMock(identifier = "private-hw-2").dpub,
              serverPublicKey = DescriptorPublicKeyMock(identifier = "private-server-2").dpub
            )
          ),
          wrappedSsek = SealedSsekFake,
          descriptorBackups = listOf(
            DescriptorBackup(
              keysetId = privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId,
              sealedDescriptor = XCiphertext("fake-sealed-descriptor-1"),
              privateWalletRootXpub = XCiphertext("fake-private-wallet-root-xpub-1")
            ),
            DescriptorBackup(
              keysetId = "private-keyset-2",
              sealedDescriptor = XCiphertext("fake-sealed-descriptor-2"),
              privateWalletRootXpub = XCiphertext("fake-private-wallet-root-xpub-2")
            )
          ),
          activeKeysetId = privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
        )
      )

      val result = service().checkPrivateKeysets(privateAccount)

      val info = result.shouldBeOk()
      info.shouldBeInstanceOf<PrivateKeysetInfo.NeedsUnsealing>()
    }

    test("returns error when private keysets require descriptor backups but none are available") {
      val privateAccount = FullAccountMock.copy(
        keybox = PrivateWalletKeyboxMock.copy(
          keysets = listOf(PrivateWalletKeyboxMock.activeSpendingKeyset),
          canUseKeyboxKeysets = false
        )
      )
      accountService.setActiveAccount(privateAccount)

      listKeysetsF8eClient.result = Ok(
        ListKeysetsResponse(
          keysets = listOf(
            PrivateMultisigRemoteKeyset(
              keysetId = privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId,
              networkType = "SIGNET",
              appPublicKey = privateAccount.keybox.activeSpendingKeyset.appKey.key.dpub,
              hardwarePublicKey = privateAccount.keybox.activeSpendingKeyset.hardwareKey.key.dpub,
              serverPublicKey =
                privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
            ),
            PrivateMultisigRemoteKeyset(
              keysetId = "private-keyset-2",
              networkType = "SIGNET",
              appPublicKey = DescriptorPublicKeyMock(identifier = "private-app-2").dpub,
              hardwarePublicKey = DescriptorPublicKeyMock(identifier = "private-hw-2").dpub,
              serverPublicKey = DescriptorPublicKeyMock(identifier = "private-server-2").dpub
            )
          ),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
        )
      )

      val result = service().checkPrivateKeysets(privateAccount)

      result.shouldBeErrOfType<KeysetRepairError.FetchKeysetsFailed>()
    }

    test("returns error when network request fails") {
      accountService.setActiveAccount(FullAccountMock)
      val networkError = HttpError.NetworkError(RuntimeException("Network unavailable"))
      listKeysetsF8eClient.result = Err(networkError)

      val result = service().checkPrivateKeysets(FullAccountMock)

      result.shouldBeErrOfType<KeysetRepairError.FetchKeysetsFailed>()
    }
  }

  context("attemptRepair") {
    test("repairs keybox successfully with legacy keysets") {
      accountService.setActiveAccount(FullAccountMock)
      val serverActiveKeysetId = "spending-public-keyset-fake-server-id-1"

      val cachedData = KeysetRepairCachedData(
        response = ListKeysetsResponse(
          keysets = listOf(
            LegacyRemoteKeyset(
              keysetId = "spending-public-keyset-fake-server-id-0",
              networkType = "SIGNET",
              appDescriptor = DescriptorPublicKeyMock(identifier = "app-0").dpub,
              hardwareDescriptor = DescriptorPublicKeyMock(identifier = "hw-0").dpub,
              serverDescriptor = DescriptorPublicKeyMock(identifier = "server-0").dpub
            ),
            LegacyRemoteKeyset(
              keysetId = serverActiveKeysetId,
              networkType = "SIGNET",
              appDescriptor = DescriptorPublicKeyMock(identifier = "app-1").dpub,
              hardwareDescriptor = DescriptorPublicKeyMock(identifier = "hw-1").dpub,
              serverDescriptor = DescriptorPublicKeyMock(identifier = "server-1").dpub
            )
          ),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = serverActiveKeysetId
        ),
        serverActiveKeysetId = serverActiveKeysetId
      )

      // Setup cloud backup
      cloudStoreAccountRepository.currentAccountResult = Ok(testCloudStoreAccount)
      // Pre-populate a backup so we can get the sealed CSEK
      cloudBackupService.writeBackup(
        accountId = FullAccountMock.accountId,
        cloudStoreAccount = testCloudStoreAccount,
        backup = CloudBackupV2WithFullAccountMock,
        requireAuthRefresh = false
      )
      fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)

      val result = service().attemptRepair(
        account = FullAccountMock,
        cachedData = cachedData
      )

      val repairComplete = result.shouldBeOk()
      repairComplete.updatedKeybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId.shouldBe(
        serverActiveKeysetId
      )
      fullAccountCloudBackupCreator.createCalls.awaitItem()
    }

    test("returns error when server active keyset not found") {
      accountService.setActiveAccount(FullAccountMock)

      val cachedData = KeysetRepairCachedData(
        response = ListKeysetsResponse(
          keysets = listOf(
            LegacyRemoteKeyset(
              keysetId = "some-keyset",
              networkType = "SIGNET",
              appDescriptor = DescriptorPublicKeyMock(identifier = "app-0").dpub,
              hardwareDescriptor = DescriptorPublicKeyMock(identifier = "hw-0").dpub,
              serverDescriptor = DescriptorPublicKeyMock(identifier = "server-0").dpub
            )
          ),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = "non-existent-keyset-id"
        ),
        serverActiveKeysetId = "non-existent-keyset-id"
      )

      val result = service().attemptRepair(
        account = FullAccountMock,
        cachedData = cachedData
      )

      result.shouldBeErrOfType<KeysetRepairError.FetchKeysetsFailed>()
    }

    test("repairs incomplete private wallet keybox directly from listKeysets response") {
      val privateAccount = FullAccountMock.copy(
        keybox = PrivateWalletKeyboxMock.copy(
          keysets = listOf(PrivateWalletKeyboxMock.activeSpendingKeyset),
          canUseKeyboxKeysets = false
        )
      )
      accountService.setActiveAccount(privateAccount)

      val activePrivateKeysetId =
        privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
      val cachedData = KeysetRepairCachedData(
        response = ListKeysetsResponse(
          keysets = listOf(
            createFakeLegacyRemoteKeyset("legacy-keyset-1"),
            PrivateMultisigRemoteKeyset(
              keysetId = activePrivateKeysetId,
              networkType = "SIGNET",
              appPublicKey = privateAccount.keybox.activeSpendingKeyset.appKey.key.dpub,
              hardwarePublicKey = privateAccount.keybox.activeSpendingKeyset.hardwareKey.key.dpub,
              serverPublicKey =
                privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
            )
          ),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = activePrivateKeysetId
        ),
        serverActiveKeysetId = activePrivateKeysetId
      )

      cloudStoreAccountRepository.currentAccountResult = Ok(testCloudStoreAccount)
      cloudBackupService.writeBackup(
        accountId = privateAccount.accountId,
        cloudStoreAccount = testCloudStoreAccount,
        backup = CloudBackupV2WithFullAccountMock,
        requireAuthRefresh = false
      )
      fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)

      val result = service().attemptRepair(
        account = privateAccount,
        cachedData = cachedData
      )

      val repairComplete = result.shouldBeOk()
      repairComplete.updatedKeybox.canUseKeyboxKeysets.shouldBe(true)
      repairComplete.updatedKeybox.activeSpendingKeyset.shouldBe(
        privateAccount.keybox.activeSpendingKeyset
      )
      repairComplete.updatedKeybox.keysets.map { it.f8eSpendingKeyset.keysetId }.sorted().shouldBe(
        listOf("legacy-keyset-1", activePrivateKeysetId).sorted()
      )
      fullAccountCloudBackupCreator.createCalls.awaitItem()
    }

    test("returns error when cloud account not available") {
      accountService.setActiveAccount(FullAccountMock)
      val serverActiveKeysetId = "spending-public-keyset-fake-server-id-0"

      val cachedData = KeysetRepairCachedData(
        response = ListKeysetsResponse(
          keysets = listOf(
            LegacyRemoteKeyset(
              keysetId = serverActiveKeysetId,
              networkType = "SIGNET",
              appDescriptor = DescriptorPublicKeyMock(identifier = "app-0").dpub,
              hardwareDescriptor = DescriptorPublicKeyMock(identifier = "hw-0").dpub,
              serverDescriptor = DescriptorPublicKeyMock(identifier = "server-0").dpub
            )
          ),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = serverActiveKeysetId
        ),
        serverActiveKeysetId = serverActiveKeysetId
      )

      // No cloud account available
      cloudStoreAccountRepository.currentAccountResult = Ok(null)

      val result = service().attemptRepair(
        account = FullAccountMock,
        cachedData = cachedData
      )

      result.shouldBeErrOfType<KeysetRepairError.CloudBackupFailed>()
    }

    test("marks sync status as Synced after successful repair") {
      accountService.setActiveAccount(FullAccountMock)
      val serverActiveKeysetId = "spending-public-keyset-fake-server-id-0"

      val cachedData = KeysetRepairCachedData(
        response = ListKeysetsResponse(
          keysets = listOf(
            LegacyRemoteKeyset(
              keysetId = serverActiveKeysetId,
              networkType = "SIGNET",
              appDescriptor = DescriptorPublicKeyMock(identifier = "app-0").dpub,
              hardwareDescriptor = DescriptorPublicKeyMock(identifier = "hw-0").dpub,
              serverDescriptor = DescriptorPublicKeyMock(identifier = "server-0").dpub
            )
          ),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = serverActiveKeysetId
        ),
        serverActiveKeysetId = serverActiveKeysetId
      )

      cloudStoreAccountRepository.currentAccountResult = Ok(testCloudStoreAccount)
      cloudBackupService.writeBackup(
        accountId = FullAccountMock.accountId,
        cloudStoreAccount = testCloudStoreAccount,
        backup = CloudBackupV2WithFullAccountMock,
        requireAuthRefresh = false
      )
      fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)

      val service = service()

      // Sync status should remain Synced after repair
      service.syncStatus.test {
        // Initial state
        awaitItem().shouldBe(SpendingKeysetSyncStatus.Synced)

        // Perform repair
        service.attemptRepair(
          account = FullAccountMock,
          cachedData = cachedData
        ).shouldBeOk()

        // Sync status should still be Synced
        expectNoEvents()
      }
      fullAccountCloudBackupCreator.createCalls.awaitItem()
    }
  }

  context("regenerateActiveKeyset") {
    test("regenerates keyset successfully") {
      accountService.setActiveAccount(FullAccountMock)
      val serverActiveKeysetId = "spending-public-keyset-fake-server-id-0"

      val cachedData = KeysetRepairCachedData(
        response = ListKeysetsResponse(
          keysets = listOf(
            LegacyRemoteKeyset(
              keysetId = serverActiveKeysetId,
              networkType = "SIGNET",
              appDescriptor = DescriptorPublicKeyMock(identifier = "app-0").dpub,
              hardwareDescriptor = DescriptorPublicKeyMock(identifier = "hw-0").dpub,
              serverDescriptor = DescriptorPublicKeyMock(identifier = "server-0").dpub
            )
          ),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = serverActiveKeysetId
        ),
        serverActiveKeysetId = serverActiveKeysetId
      )

      // Setup cloud backup
      cloudStoreAccountRepository.currentAccountResult = Ok(testCloudStoreAccount)
      cloudBackupService.writeBackup(
        accountId = FullAccountMock.accountId,
        cloudStoreAccount = testCloudStoreAccount,
        backup = CloudBackupV2WithFullAccountMock,
        requireAuthRefresh = false
      )
      fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)

      val result = service().regenerateActiveKeyset(
        account = FullAccountMock,
        updatedKeybox = FullAccountMock.keybox,
        hwSpendingKey = HwSpendingPublicKeyMock,
        hwProofOfPossession = HwFactorProofOfPossession("fake-proof"),
        cachedData = cachedData
      )

      val repairComplete = result.shouldBeOk()
      // The new keyset should be the active one
      repairComplete.updatedKeybox.activeSpendingKeyset.hardwareKey.shouldBe(HwSpendingPublicKeyMock)
      // Old keysets should be preserved for sweep
      repairComplete.updatedKeybox.keysets.size.shouldBe(
        FullAccountMock.keybox.keysets.size + 1
      )
      setActiveSpendingKeysetF8eClient.lastSetArguments
        .shouldNotBeNull()
        .proof
        .shouldBeInstanceOf<PrivilegedActionProof.HwKeyProof>()
        .hwFactorProofOfPossession
        .shouldBe(HwFactorProofOfPossession("fake-proof"))
      fullAccountCloudBackupCreator.createCalls.awaitItem()
    }

    test("regenerates keyset with descriptor backup when SSEK available") {
      accountService.setActiveAccount(FullAccountMock)
      val serverActiveKeysetId = "spending-public-keyset-fake-server-id-0"

      val descriptorBackup = DescriptorBackup(
        keysetId = serverActiveKeysetId,
        sealedDescriptor = XCiphertext("fake-sealed-descriptor"),
        privateWalletRootXpub = XCiphertext("fake-private-wallet-root-xpub")
      )

      val cachedData = KeysetRepairCachedData(
        response = ListKeysetsResponse(
          keysets = listOf(
            LegacyRemoteKeyset(
              keysetId = serverActiveKeysetId,
              networkType = "SIGNET",
              appDescriptor = DescriptorPublicKeyMock(identifier = "app-0").dpub,
              hardwareDescriptor = DescriptorPublicKeyMock(identifier = "hw-0").dpub,
              serverDescriptor = DescriptorPublicKeyMock(identifier = "server-0").dpub
            )
          ),
          wrappedSsek = SealedSsekFake,
          descriptorBackups = listOf(descriptorBackup),
          activeKeysetId = serverActiveKeysetId
        ),
        serverActiveKeysetId = serverActiveKeysetId
      )

      // Setup cloud backup
      cloudStoreAccountRepository.currentAccountResult = Ok(testCloudStoreAccount)
      cloudBackupService.writeBackup(
        accountId = FullAccountMock.accountId,
        cloudStoreAccount = testCloudStoreAccount,
        backup = CloudBackupV2WithFullAccountMock,
        requireAuthRefresh = false
      )
      fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)

      val result = service().regenerateActiveKeyset(
        account = FullAccountMock,
        updatedKeybox = FullAccountMock.keybox,
        hwSpendingKey = HwSpendingPublicKeyMock,
        hwProofOfPossession = HwFactorProofOfPossession("fake-proof"),
        cachedData = cachedData
      )

      result.shouldBeOk()
      // Descriptor backup is uploaded internally when SSEK is available
      descriptorBackupService.lastUploadDescriptorBackupsArgs
        .shouldNotBeNull()
        .proof
        .shouldBeInstanceOf<PrivilegedActionProof.HwKeyProof>()
        .hwFactorProofOfPossession
        .shouldBe(HwFactorProofOfPossession("fake-proof"))
      fullAccountCloudBackupCreator.createCalls.awaitItem()
    }

    test("regenerates W3 keyset with action proofs") {
      accountService.setActiveAccount(FullAccountW3Mock)
      val serverActiveKeysetId = "spending-public-keyset-fake-server-id-0"

      val descriptorBackup = DescriptorBackup(
        keysetId = serverActiveKeysetId,
        sealedDescriptor = XCiphertext("fake-sealed-descriptor"),
        privateWalletRootXpub = XCiphertext("fake-private-wallet-root-xpub")
      )

      val cachedData = KeysetRepairCachedData(
        response = ListKeysetsResponse(
          keysets = listOf(
            LegacyRemoteKeyset(
              keysetId = serverActiveKeysetId,
              networkType = "SIGNET",
              appDescriptor = DescriptorPublicKeyMock(identifier = "app-0").dpub,
              hardwareDescriptor = DescriptorPublicKeyMock(identifier = "hw-0").dpub,
              serverDescriptor = DescriptorPublicKeyMock(identifier = "server-0").dpub
            )
          ),
          wrappedSsek = SealedSsekFake,
          descriptorBackups = listOf(descriptorBackup),
          activeKeysetId = serverActiveKeysetId
        ),
        serverActiveKeysetId = serverActiveKeysetId
      )

      listKeysetsF8eClient.result = Ok(cachedData.response)
      setActiveSpendingKeysetF8eClient.setResult = Ok(SignedKeysetVerificationResponseMock)
      cloudStoreAccountRepository.currentAccountResult = Ok(testCloudStoreAccount)
      cloudBackupService.writeBackup(
        accountId = FullAccountW3Mock.accountId,
        cloudStoreAccount = testCloudStoreAccount,
        backup = CloudBackupV2WithFullAccountMock,
        requireAuthRefresh = false
      )
      fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)

      val service = service()
      val prepared = service.prepareRegeneratedActiveKeyset(
        account = FullAccountW3Mock,
        updatedKeybox = FullAccountW3Mock.keybox,
        hwSpendingKey = HwSpendingPublicKeyMock
      ).shouldBeOk()
      val descriptorProof = PrivilegedActionProof.HwSignedAction(
        ActionProofHeader(signatures = listOf("descriptor-signature"), nonce = "db-nonce")
      )
      val activationProof = PrivilegedActionProof.HwSignedAction(
        ActionProofHeader(signatures = listOf("activation-signature"), nonce = "ak-nonce")
      )

      val result = service.completeRegeneratedActiveKeyset(
        account = FullAccountW3Mock,
        preparedRegeneratedKeyset = prepared,
        descriptorBackupProof = descriptorProof,
        keysetActivationProof = activationProof,
        cachedData = cachedData
      )

      val repairComplete = result.shouldBeOk()
      repairComplete.signedKeysetVerification.shouldBe(SignedKeysetVerificationResponseMock)
      descriptorBackupService.lastUploadDescriptorBackupsArgs
        .shouldNotBeNull()
        .proof
        .shouldBe(descriptorProof)
      setActiveSpendingKeysetF8eClient.lastSetArguments
        .shouldNotBeNull()
        .proof
        .shouldBe(activationProof)
      fullAccountCloudBackupCreator.createCalls.awaitItem()
    }

    test("W3 one-shot regeneration rejects legacy hardware proof") {
      val cachedData = KeysetRepairCachedData(
        response = ListKeysetsResponse(
          keysets = emptyList(),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = "server-keyset"
        ),
        serverActiveKeysetId = "server-keyset"
      )

      val result = service().regenerateActiveKeyset(
        account = FullAccountW3Mock,
        updatedKeybox = FullAccountW3Mock.keybox,
        hwSpendingKey = HwSpendingPublicKeyMock,
        hwProofOfPossession = HwFactorProofOfPossession("fake-proof"),
        cachedData = cachedData
      )

      result.shouldBeErrOfType<KeysetRepairError.KeysetActivationFailed>()
    }

    test("returns error when app key generation fails") {
      accountService.setActiveAccount(FullAccountMock)
      val serverActiveKeysetId = "spending-public-keyset-fake-server-id-0"

      val cachedData = KeysetRepairCachedData(
        response = ListKeysetsResponse(
          keysets = emptyList(),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = serverActiveKeysetId
        ),
        serverActiveKeysetId = serverActiveKeysetId
      )

      appKeysGenerator.keyBundleResult = Err(RuntimeException("Key generation failed"))

      val result = service().regenerateActiveKeyset(
        account = FullAccountMock,
        updatedKeybox = FullAccountMock.keybox,
        hwSpendingKey = HwSpendingPublicKeyMock,
        hwProofOfPossession = HwFactorProofOfPossession("fake-proof"),
        cachedData = cachedData
      )

      result.shouldBeErrOfType<KeysetRepairError.SaveKeyboxFailed>()
    }

    test("returns error when server keyset creation fails") {
      accountService.setActiveAccount(FullAccountMock)
      val serverActiveKeysetId = "spending-public-keyset-fake-server-id-0"

      val cachedData = KeysetRepairCachedData(
        response = ListKeysetsResponse(
          keysets = emptyList(),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = serverActiveKeysetId
        ),
        serverActiveKeysetId = serverActiveKeysetId
      )

      createAccountKeysetV2F8eClient.createKeysetResult =
        Err(HttpError.NetworkError(RuntimeException("Network error")))

      val result = service().regenerateActiveKeyset(
        account = FullAccountMock,
        updatedKeybox = FullAccountMock.keybox,
        hwSpendingKey = HwSpendingPublicKeyMock,
        hwProofOfPossession = HwFactorProofOfPossession("fake-proof"),
        cachedData = cachedData
      )

      result.shouldBeErrOfType<KeysetRepairError.FetchKeysetsFailed>()
    }

    test("returns error when keyset activation fails") {
      accountService.setActiveAccount(FullAccountMock)
      val serverActiveKeysetId = "spending-public-keyset-fake-server-id-0"

      val cachedData = KeysetRepairCachedData(
        response = ListKeysetsResponse(
          keysets = emptyList(),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = serverActiveKeysetId
        ),
        serverActiveKeysetId = serverActiveKeysetId
      )

      setActiveSpendingKeysetF8eClient.setResult =
        Err(HttpError.NetworkError(RuntimeException("Network error")))

      val result = service().regenerateActiveKeyset(
        account = FullAccountMock,
        updatedKeybox = FullAccountMock.keybox,
        hwSpendingKey = HwSpendingPublicKeyMock,
        hwProofOfPossession = HwFactorProofOfPossession("fake-proof"),
        cachedData = cachedData
      )

      result.shouldBeErrOfType<KeysetRepairError.KeysetActivationFailed>()
    }

    test("returns error when cloud account not available") {
      accountService.setActiveAccount(FullAccountMock)
      val serverActiveKeysetId = "spending-public-keyset-fake-server-id-0"

      val cachedData = KeysetRepairCachedData(
        response = ListKeysetsResponse(
          keysets = emptyList(),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = serverActiveKeysetId
        ),
        serverActiveKeysetId = serverActiveKeysetId
      )

      // No cloud account available
      cloudStoreAccountRepository.currentAccountResult = Ok(null)

      val result = service().regenerateActiveKeyset(
        account = FullAccountMock,
        updatedKeybox = FullAccountMock.keybox,
        hwSpendingKey = HwSpendingPublicKeyMock,
        hwProofOfPossession = HwFactorProofOfPossession("fake-proof"),
        cachedData = cachedData
      )

      result.shouldBeErrOfType<KeysetRepairError.CloudBackupFailed>()
    }

    test("marks sync status as Synced after successful regeneration") {
      accountService.setActiveAccount(FullAccountMock)
      val serverActiveKeysetId = "spending-public-keyset-fake-server-id-0"

      val cachedData = KeysetRepairCachedData(
        response = ListKeysetsResponse(
          keysets = emptyList(),
          wrappedSsek = null,
          descriptorBackups = emptyList(),
          activeKeysetId = serverActiveKeysetId
        ),
        serverActiveKeysetId = serverActiveKeysetId
      )

      cloudStoreAccountRepository.currentAccountResult = Ok(testCloudStoreAccount)
      cloudBackupService.writeBackup(
        accountId = FullAccountMock.accountId,
        cloudStoreAccount = testCloudStoreAccount,
        backup = CloudBackupV2WithFullAccountMock,
        requireAuthRefresh = false
      )
      fullAccountCloudBackupCreator.backupResult = Ok(CloudBackupV2WithFullAccountMock)

      val service = service()

      service.syncStatus.test {
        // Initial state
        awaitItem().shouldBe(SpendingKeysetSyncStatus.Synced)

        // Perform regeneration
        service.regenerateActiveKeyset(
          account = FullAccountMock,
          updatedKeybox = FullAccountMock.keybox,
          hwSpendingKey = HwSpendingPublicKeyMock,
          hwProofOfPossession = HwFactorProofOfPossession("fake-proof"),
          cachedData = cachedData
        ).shouldBeOk()

        // Sync status should still be Synced
        expectNoEvents()
      }
      fullAccountCloudBackupCreator.createCalls.awaitItem()
    }
  }
})
