package bitkey.recovery

import bitkey.account.AccountConfigServiceFake
import bitkey.account.LiteAccountConfig
import bitkey.backup.DescriptorBackup
import bitkey.f8e.account.UpdateDescriptorBackupsF8eClientFake
import bitkey.recovery.DescriptorBackupError.SsekNotFound
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderImpl
import build.wallet.bitcoin.transactions.BitcoinWalletServiceFake
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.PrivateWalletKeyboxMock
import build.wallet.bitkey.spending.PrivateSpendingKeysetMock
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.cloud.backup.csek.SsekDaoFake
import build.wallet.cloud.backup.csek.SsekFake
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.SymmetricKeyEncryptorFake
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.LegacyRemoteKeyset
import build.wallet.f8e.recovery.ListKeysetsF8eClientMock
import build.wallet.f8e.recovery.ListKeysetsResponse
import build.wallet.f8e.recovery.PrivateMultisigRemoteKeyset
import build.wallet.f8e.recovery.RemoteKeyset
import build.wallet.f8e.recovery.toSpendingKeysets
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.feature.flags.DescriptorBackupFailsafeFeatureFlag
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.recovery.DescriptorBackupVerificationDaoFake
import build.wallet.recovery.createFakeLegacyRemoteKeyset
import build.wallet.recovery.createFakeSpendingKeyset
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import okio.ByteString.Companion.encodeUtf8

@Suppress("LargeClass")
class DescriptorBackupServiceImplTests : FunSpec({
  val ssekDao = SsekDaoFake()
  val symmetricKeyEncryptor = SymmetricKeyEncryptorFake()
  val uuidGenerator = UuidGeneratorFake()
  val accountConfigService = AccountConfigServiceFake()
  val listKeysetsF8eClient = ListKeysetsF8eClientMock()
  val accountService = AccountServiceFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val descriptorBackupFailsafeFeatureFlag = DescriptorBackupFailsafeFeatureFlag(featureFlagDao)
  val descriptorBackupVerificationDao = DescriptorBackupVerificationDaoFake()
  val bitcoinWalletService = BitcoinWalletServiceFake()

  val service = DescriptorBackupServiceImpl(
    ssekDao = ssekDao,
    symmetricKeyEncryptor = symmetricKeyEncryptor,
    accountConfigService = accountConfigService,
    uuidGenerator = uuidGenerator,
    bitcoinMultiSigDescriptorBuilder = BitcoinMultiSigDescriptorBuilderImpl(),
    listKeysetsF8eClient = listKeysetsF8eClient,
    updateDescriptorBackupsF8eClient = UpdateDescriptorBackupsF8eClientFake(),
    accountService = accountService,
    descriptorBackupFailsafeFeatureFlag = descriptorBackupFailsafeFeatureFlag,
    descriptorBackupVerificationDao = descriptorBackupVerificationDao,
    bitcoinWalletService = bitcoinWalletService
  )

  val privateAccount = FullAccountMock.copy(keybox = PrivateWalletKeyboxMock)

  beforeTest {
    uuidGenerator.reset()
    symmetricKeyEncryptor.reset()
    ssekDao.reset()
    accountConfigService.reset()
    listKeysetsF8eClient.reset()
    accountService.reset()
    featureFlagDao.reset()
    descriptorBackupVerificationDao.reset()
    bitcoinWalletService.reset()
  }

  test("seal and unseal descriptors should return original data") {
    ssekDao.set(SealedSsekFake, SsekFake)
    val originalKeysets = listOf(
      createFakeSpendingKeyset("keyset-1"),
      createFakeSpendingKeyset("keyset-2")
    )
    val appSpendingKey = originalKeysets[0].appKey.key
    val hwSpendingKey = originalKeysets[0].hardwareKey.key
    val f8eSpendingKey = originalKeysets[0].f8eSpendingKeyset.spendingPublicKey

    val encryptedDescriptors =
      service.sealDescriptors(SealedSsekFake, originalKeysets).get().shouldNotBeNull()
    encryptedDescriptors.map { it.keysetId }.shouldContainExactly("keyset-1", "keyset-2")
    encryptedDescriptors.forEach { descriptor ->
      descriptor.privateWalletRootXpub.shouldNotBeNull()
    }

    val unsealResult = service.unsealDescriptors(SealedSsekFake, encryptedDescriptors)
    val restoredKeysets = unsealResult.get().shouldNotBeNull()

    restoredKeysets.size shouldBe 2

    // Verify first keyset
    restoredKeysets[0].apply {
      f8eSpendingKeyset.keysetId shouldBe "keyset-1"
      appKey.key shouldBe appSpendingKey
      hardwareKey.key shouldBe hwSpendingKey
      f8eSpendingKeyset.spendingPublicKey shouldBe f8eSpendingKey
      f8eSpendingKeyset.privateWalletRootXpub shouldBe
        originalKeysets[0].f8eSpendingKeyset.privateWalletRootXpub
    }

    // Verify second keyset
    with(restoredKeysets[1]) {
      f8eSpendingKeyset.keysetId shouldBe "keyset-2"
      appKey.key shouldBe appSpendingKey
      hardwareKey.key shouldBe hwSpendingKey
      f8eSpendingKeyset.spendingPublicKey shouldBe f8eSpendingKey
      f8eSpendingKeyset.privateWalletRootXpub shouldBe
        originalKeysets[1].f8eSpendingKeyset.privateWalletRootXpub
    }
  }

  test("sealDescriptors handles optional private wallet root xpub values") {
    ssekDao.set(SealedSsekFake, SsekFake)
    val keysetWithXpub =
      createFakeSpendingKeyset(
        keysetId = "keyset-with-xpub",
        privateWalletRootXpub = "tpub-private-xpub"
      )
    val keysetWithoutXpub =
      createFakeSpendingKeyset(
        keysetId = "keyset-without-xpub",
        privateWalletRootXpub = null
      )

    val encryptedDescriptors =
      service.sealDescriptors(
        sealedSsek = SealedSsekFake,
        keysets = listOf(keysetWithXpub, keysetWithoutXpub)
      ).get().shouldNotBeNull()

    encryptedDescriptors.first { it.keysetId == "keyset-with-xpub" }
      .privateWalletRootXpub.shouldNotBeNull()
    encryptedDescriptors.first { it.keysetId == "keyset-without-xpub" }
      .privateWalletRootXpub shouldBe null

    val restoredKeysets =
      service.unsealDescriptors(
        sealedSsek = SealedSsekFake,
        encryptedDescriptorBackups = encryptedDescriptors
      ).get().shouldNotBeNull()

    restoredKeysets.first { it.f8eSpendingKeyset.keysetId == "keyset-with-xpub" }
      .f8eSpendingKeyset.privateWalletRootXpub shouldBe
      keysetWithXpub.f8eSpendingKeyset.privateWalletRootXpub

    restoredKeysets.first { it.f8eSpendingKeyset.keysetId == "keyset-without-xpub" }
      .f8eSpendingKeyset.privateWalletRootXpub shouldBe null
  }

  context("seal/unseal descriptor matrix for private wallet root xpub") {
    listOf(null, "foo").forEach { privateWalletRootXpub ->
      test("privateWalletRootXpub = ${privateWalletRootXpub ?: "null"}") {
        ssekDao.set(SealedSsekFake, SsekFake)
        val keyset =
          createFakeSpendingKeyset(
            keysetId = "matrix-keyset-${privateWalletRootXpub ?: "null"}",
            privateWalletRootXpub = privateWalletRootXpub
          )

        val encryptedDescriptors =
          service.sealDescriptors(
            sealedSsek = SealedSsekFake,
            keysets = listOf(keyset)
          ).get().shouldNotBeNull()

        val encryptedDescriptor = encryptedDescriptors.single()

        if (privateWalletRootXpub == null) {
          encryptedDescriptor.privateWalletRootXpub shouldBe null
        } else {
          encryptedDescriptor.privateWalletRootXpub.shouldNotBeNull()
        }

        val restoredKeysets =
          service.unsealDescriptors(
            sealedSsek = SealedSsekFake,
            encryptedDescriptorBackups = encryptedDescriptors
          ).get().shouldNotBeNull()

        restoredKeysets.single().f8eSpendingKeyset.privateWalletRootXpub shouldBe
          privateWalletRootXpub
      }
    }
  }

  test("sealDescriptors returns error when SSEK is not found") {
    service.sealDescriptors(
      sealedSsek = SealedSsekFake,
      keysets = listOf(createFakeSpendingKeyset("keyset-1"))
    ).shouldBeErr(SsekNotFound)
  }

  test("unsealDescriptors returns error when SSEK is not found") {
    val encryptedBackup = DescriptorBackup(
      keysetId = "keyset-1",
      sealedDescriptor = XCiphertext("xciphertext"),
      privateWalletRootXpub = null
    )

    service.unsealDescriptors(SealedSsekFake, listOf(encryptedBackup))
      .shouldBeErr(SsekNotFound)
  }

  test("prepareDescriptorBackupsForRecovery for hardware recovery with incomplete local keysets") {
    val accountId = FullAccountId("test-account")
    val newKeyset = createFakeSpendingKeyset("new-keyset")
    val remoteKeysets =
      listOf(newKeyset, createFakeSpendingKeyset("existing-keyset"))
        .map { keyset ->

          LegacyRemoteKeyset(
            keysetId = keyset.f8eSpendingKeyset.keysetId,
            networkType = keyset.networkType.name,
            appDescriptor = keyset.appKey.key.dpub,
            hardwareDescriptor = keyset.hardwareKey.key.dpub,
            serverDescriptor = keyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
          )
        }

    accountService.accountState.value = Ok(
      ActiveAccount(
        FullAccountMock.copy(
          keybox = FullAccountMock.keybox.copy(canUseKeyboxKeysets = false)
        )
      )
    )

    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = remoteKeysets,
        descriptorBackups = emptyList(),
        wrappedSsek = null
      )
    )

    val result = service.prepareDescriptorBackupsForRecovery(
      accountId = accountId,
      factorToRecover = PhysicalFactor.Hardware,
      f8eSpendingKeyset = newKeyset.f8eSpendingKeyset,
      appSpendingKey = newKeyset.appKey,
      hwSpendingKey = newKeyset.hardwareKey
    ).get()

    val encryptOnlyResult =
      result.shouldNotBeNull().shouldBeInstanceOf<DescriptorBackupPreparedData.EncryptOnly>()

    encryptOnlyResult.keysetsToEncrypt.size shouldBe remoteKeysets.size
    encryptOnlyResult.keysetsToEncrypt.zip(remoteKeysets).forEach { (local, remote) ->
      local.f8eSpendingKeyset.keysetId shouldBe remote.keysetId
      local.appKey.key.dpub shouldBe remote.appDescriptor
      local.hardwareKey.key.dpub shouldBe remote.hardwareDescriptor
      local.f8eSpendingKeyset.spendingPublicKey.key.dpub shouldBe remote.serverDescriptor
    }
  }

  test("prepareDescriptorBackupsForRecovery for hardware recovery with local keysets") {
    val accountId = FullAccountId("test-account")
    val newKeyset = createFakeSpendingKeyset("new-keyset", "uuid-0")

    accountService.accountState.value = Ok(ActiveAccount(FullAccountMock))

    val result = service.prepareDescriptorBackupsForRecovery(
      accountId = accountId,
      factorToRecover = PhysicalFactor.Hardware,
      f8eSpendingKeyset = newKeyset.f8eSpendingKeyset,
      appSpendingKey = newKeyset.appKey,
      hwSpendingKey = newKeyset.hardwareKey
    ).get()

    result.shouldNotBeNull().shouldBeInstanceOf<DescriptorBackupPreparedData.EncryptOnly>()
      .keysetsToEncrypt shouldBe listOf(FullAccountMock.keybox.activeSpendingKeyset, newKeyset)
  }

  test("prepareDescriptorBackupsForRecovery for app recovery with existing descriptors") {
    val accountId = FullAccountId("test-account")
    val newKeyset = createFakeSpendingKeyset("new-keyset")
    val existingKeyset = createFakeSpendingKeyset("existing-keyset")
    val existingDescriptors = listOf(
      DescriptorBackup(
        keysetId = "existing-keyset",
        sealedDescriptor = XCiphertext("encrypted"),
        privateWalletRootXpub = null
      )
    )

    val remoteKeysets = listOf(existingKeyset, newKeyset).map { keyset ->
      LegacyRemoteKeyset(
        keysetId = keyset.f8eSpendingKeyset.keysetId,
        networkType = keyset.networkType.name,
        appDescriptor = keyset.appKey.key.dpub,
        hardwareDescriptor = keyset.hardwareKey.key.dpub,
        serverDescriptor = keyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
      )
    }

    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = remoteKeysets,
        descriptorBackups = existingDescriptors,
        wrappedSsek = SealedSsekFake
      )
    )

    val result = service.prepareDescriptorBackupsForRecovery(
      accountId = accountId,
      factorToRecover = PhysicalFactor.App,
      f8eSpendingKeyset = newKeyset.f8eSpendingKeyset,
      appSpendingKey = newKeyset.appKey,
      hwSpendingKey = newKeyset.hardwareKey
    ).get()

    result.shouldNotBeNull().shouldBeInstanceOf<DescriptorBackupPreparedData.NeedsUnsealed>()
      .apply {
        descriptorsToDecrypt shouldContainExactly existingDescriptors
        keysetsToEncrypt.size shouldBe 1
        keysetsToEncrypt.single().let { encryptedKeyset ->
          val remote = remoteKeysets[1]
          encryptedKeyset.f8eSpendingKeyset.keysetId shouldBe remote.keysetId
          encryptedKeyset.appKey.key.dpub shouldBe remote.appDescriptor
          encryptedKeyset.hardwareKey.key.dpub shouldBe remote.hardwareDescriptor
          encryptedKeyset.f8eSpendingKeyset.spendingPublicKey.key.dpub shouldBe remote.serverDescriptor
        }
        sealedSsek shouldBe SealedSsekFake
      }
  }

  test("prepareDescriptorBackupsForRecovery for app recovery with available SSEK") {
    val accountId = FullAccountId("test-account")
    val newKeyset = createFakeSpendingKeyset("new-keyset")
    val existingKeyset = createFakeSpendingKeyset("existing-keyset")
    val existingDescriptors = listOf(
      DescriptorBackup(
        keysetId = "existing-keyset",
        sealedDescriptor = XCiphertext("encrypted"),
        privateWalletRootXpub = null
      )
    )

    // Set up SSEK in storage
    ssekDao.set(SealedSsekFake, SsekFake)

    val remoteKeysets = listOf(existingKeyset, newKeyset).map { keyset ->

      LegacyRemoteKeyset(
        keysetId = keyset.f8eSpendingKeyset.keysetId,
        networkType = keyset.networkType.name,
        appDescriptor = keyset.appKey.key.dpub,
        hardwareDescriptor = keyset.hardwareKey.key.dpub,
        serverDescriptor = keyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
      )
    }

    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = remoteKeysets,
        descriptorBackups = existingDescriptors,
        wrappedSsek = SealedSsekFake
      )
    )

    val result = service.prepareDescriptorBackupsForRecovery(
      accountId = accountId,
      factorToRecover = PhysicalFactor.App,
      f8eSpendingKeyset = newKeyset.f8eSpendingKeyset,
      appSpendingKey = newKeyset.appKey,
      hwSpendingKey = newKeyset.hardwareKey
    ).get()

    result.shouldNotBeNull().shouldBeInstanceOf<DescriptorBackupPreparedData.Available>().apply {
      descriptorsToDecrypt shouldContainExactly existingDescriptors
      keysetsToEncrypt.size shouldBe 1
      keysetsToEncrypt.single().let { encryptedKeyset ->
        val remote = remoteKeysets[1]
        encryptedKeyset.f8eSpendingKeyset.keysetId shouldBe remote.keysetId
        encryptedKeyset.appKey.key.dpub shouldBe remote.appDescriptor
        encryptedKeyset.hardwareKey.key.dpub shouldBe remote.hardwareDescriptor
        encryptedKeyset.f8eSpendingKeyset.spendingPublicKey.key.dpub shouldBe remote.serverDescriptor
      }
      sealedSsek shouldBe SealedSsekFake
    }
  }

  test("prepareDescriptorBackupsForRecovery for app recovery with no descriptor backups") {
    val accountId = FullAccountId("test-account")
    val newKeyset = createFakeSpendingKeyset("new-keyset")
    val remoteKeysets = listOf(newKeyset, createFakeSpendingKeyset("f8e-keyset")).map { keyset ->

      LegacyRemoteKeyset(
        keysetId = keyset.f8eSpendingKeyset.keysetId,
        networkType = keyset.networkType.name,
        appDescriptor = keyset.appKey.key.dpub,
        hardwareDescriptor = keyset.hardwareKey.key.dpub,
        serverDescriptor = keyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
      )
    }

    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = remoteKeysets,
        descriptorBackups = emptyList(), // No existing descriptor backups
        wrappedSsek = null // No wrapped SSEK
      )
    )

    val result = service.prepareDescriptorBackupsForRecovery(
      accountId = accountId,
      factorToRecover = PhysicalFactor.App,
      f8eSpendingKeyset = newKeyset.f8eSpendingKeyset,
      appSpendingKey = newKeyset.appKey,
      hwSpendingKey = newKeyset.hardwareKey
    ).get()

    val encryptOnlyResult =
      result.shouldNotBeNull().shouldBeInstanceOf<DescriptorBackupPreparedData.EncryptOnly>()

    encryptOnlyResult.keysetsToEncrypt.size shouldBe remoteKeysets.size
    encryptOnlyResult.keysetsToEncrypt.zip(remoteKeysets).forEach { (local, remote) ->
      local.f8eSpendingKeyset.keysetId shouldBe remote.keysetId
      local.appKey.key.dpub shouldBe remote.appDescriptor
      local.hardwareKey.key.dpub shouldBe remote.hardwareDescriptor
      local.f8eSpendingKeyset.spendingPublicKey.key.dpub shouldBe remote.serverDescriptor
    }
  }

  test("uploadDescriptorBackups successfully processes and uploads descriptors") {
    val accountId = FullAccountId("test-account")
    val existingKeyset = createFakeSpendingKeyset("existing-keyset")
    val newKeyset = createFakeSpendingKeyset("new-keyset")

    // Set up SSEKs
    ssekDao.set(SealedSsekFake, SsekFake)

    // First encrypt the existing keyset
    val existingDescriptor = service.sealDescriptors(
      sealedSsek = SealedSsekFake,
      keysets = listOf(existingKeyset)
    ).getOrThrow().first()

    // Set up listKeysetsF8eClient to return both backups after upload
    val allKeysets = listOf(existingKeyset, newKeyset)
    val allDescriptors = service.sealDescriptors(SealedSsekFake, allKeysets).getOrThrow()
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = allKeysets.map { keyset ->

          LegacyRemoteKeyset(
            keysetId = keyset.f8eSpendingKeyset.keysetId,
            networkType = keyset.networkType.name,
            appDescriptor = keyset.appKey.key.dpub,
            hardwareDescriptor = keyset.hardwareKey.key.dpub,
            serverDescriptor = keyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
          )
        },
        descriptorBackups = allDescriptors,
        wrappedSsek = SealedSsekFake
      )
    )

    val result = service.uploadDescriptorBackups(
      accountId = accountId,
      sealedSsekForDecryption = SealedSsekFake,
      sealedSsekForEncryption = SealedSsekFake,
      appAuthKey = PublicKey("app-auth-key"),
      hwKeyProof = HwFactorProofOfPossession("hw-proof"),
      descriptorsToDecrypt = listOf(existingDescriptor),
      keysetsToEncrypt = listOf(newKeyset)
    ).get()

    result.shouldNotBeNull()
    result.size shouldBe 2
    result[0].f8eSpendingKeyset.keysetId shouldBe "existing-keyset"
    result[1].f8eSpendingKeyset.keysetId shouldBe "new-keyset"
  }

  test("prepareDescriptorBackupsForRecovery - app recovery dedupes with already uploaded backups") {
    val accountId = FullAccountId("test-account")
    val duplicateKeyset = createFakeSpendingKeyset("keyset-2")
    val keyset1 = createFakeSpendingKeyset("keyset-1")
    val keyset3 = createFakeSpendingKeyset("keyset-3")
    val existingDescriptors = listOf(
      DescriptorBackup(
        keysetId = "keyset-1",
        sealedDescriptor = XCiphertext("encrypted-1"),
        privateWalletRootXpub = null
      ),
      DescriptorBackup(
        keysetId = "keyset-2", // Matches our new keyset
        sealedDescriptor = XCiphertext("encrypted-2"),
        privateWalletRootXpub = null
      ),
      DescriptorBackup(
        keysetId = "keyset-3",
        sealedDescriptor = XCiphertext("encrypted-3"),
        privateWalletRootXpub = null
      )
    )

    val remoteKeysets = listOf(keyset1, duplicateKeyset, keyset3).map { keyset ->

      LegacyRemoteKeyset(
        keysetId = keyset.f8eSpendingKeyset.keysetId,
        networkType = keyset.networkType.name,
        appDescriptor = keyset.appKey.key.dpub,
        hardwareDescriptor = keyset.hardwareKey.key.dpub,
        serverDescriptor = keyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
      )
    }

    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = remoteKeysets,
        descriptorBackups = existingDescriptors,
        wrappedSsek = SealedSsekFake
      )
    )

    val result = service.prepareDescriptorBackupsForRecovery(
      accountId = accountId,
      factorToRecover = PhysicalFactor.App,
      f8eSpendingKeyset = duplicateKeyset.f8eSpendingKeyset,
      appSpendingKey = duplicateKeyset.appKey,
      hwSpendingKey = duplicateKeyset.hardwareKey
    ).get()

    result.shouldNotBeNull().shouldBeInstanceOf<DescriptorBackupPreparedData.NeedsUnsealed>()
      .apply {
        descriptorsToDecrypt shouldContainExactly existingDescriptors
        keysetsToEncrypt shouldBe emptyList() // Should be empty due to duplicate keyset-2
        sealedSsek shouldBe SealedSsekFake
      }
  }

  test("uploadOnboardingDescriptorBackup verifies uploaded backups successfully") {
    val accountId = FullAccountId("test-account")
    val keyset = createFakeSpendingKeyset("test-keyset")

    ssekDao.set(SealedSsekFake, SsekFake)

    // Set up the listKeysetsF8eClient to return the uploaded backup.
    val encryptedBackup =
      service.sealDescriptors(SealedSsekFake, listOf(keyset)).getOrThrow().first()
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = listOf(
          LegacyRemoteKeyset(
            keysetId = keyset.f8eSpendingKeyset.keysetId,
            networkType = keyset.networkType.name,
            appDescriptor = keyset.appKey.key.dpub,
            hardwareDescriptor = keyset.hardwareKey.key.dpub,
            serverDescriptor = keyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
          )
        ),
        descriptorBackups = listOf(encryptedBackup),
        wrappedSsek = SealedSsekFake
      )
    )

    val result = service.uploadOnboardingDescriptorBackup(
      accountId = accountId,
      sealedSsekForEncryption = SealedSsekFake,
      appAuthKey = PublicKey("app-auth-key"),
      keysetsToEncrypt = listOf(keyset)
    )

    result.shouldBeOk()
  }

  test("uploadOnboardingDescriptorBackup fails verification when no backups found") {
    val accountId = FullAccountId("test-account")
    val keyset = createFakeSpendingKeyset("test-keyset")

    ssekDao.set(SealedSsekFake, SsekFake)

    // Set up listKeysetsF8eClient to return no backups.
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = listOf(
          LegacyRemoteKeyset(
            keysetId = keyset.f8eSpendingKeyset.keysetId,
            networkType = keyset.networkType.name,
            appDescriptor = keyset.appKey.key.dpub,
            hardwareDescriptor = keyset.hardwareKey.key.dpub,
            serverDescriptor = keyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
          )
        ),
        descriptorBackups = emptyList(),
        wrappedSsek = SealedSsekFake
      )
    )

    val result = service.uploadOnboardingDescriptorBackup(
      accountId = accountId,
      sealedSsekForEncryption = SealedSsekFake,
      appAuthKey = PublicKey("app-auth-key"),
      keysetsToEncrypt = listOf(keyset)
    )

    result.shouldBeErrOfType<DescriptorBackupError.VerificationFailed>()
      .message shouldBe "Descriptor backup count mismatch: expected 1, got 0"
  }

  test("uploadOnboardingDescriptorBackup fails verification when keyset count mismatch") {
    val accountId = FullAccountId("test-account")
    val keyset = createFakeSpendingKeyset("test-keyset")

    ssekDao.set(SealedSsekFake, SsekFake)

    // Set up listKeysetsF8eClient to return wrong number of backups.
    val encryptedBackup1 =
      service.sealDescriptors(SealedSsekFake, listOf(keyset)).getOrThrow().first()
    val encryptedBackup2 = DescriptorBackup("extra-keyset", XCiphertext("extra"), null)
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = listOf(
          LegacyRemoteKeyset(
            keysetId = keyset.f8eSpendingKeyset.keysetId,
            networkType = keyset.networkType.name,
            appDescriptor = keyset.appKey.key.dpub,
            hardwareDescriptor = keyset.hardwareKey.key.dpub,
            serverDescriptor = keyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
          )
        ),
        descriptorBackups = listOf(encryptedBackup1, encryptedBackup2),
        wrappedSsek = SealedSsekFake
      )
    )

    val result = service.uploadOnboardingDescriptorBackup(
      accountId = accountId,
      sealedSsekForEncryption = SealedSsekFake,
      appAuthKey = PublicKey("app-auth-key"),
      keysetsToEncrypt = listOf(keyset)
    )

    result.shouldBeErrOfType<DescriptorBackupError.VerificationFailed>()
      .message shouldBe "Descriptor backup count mismatch: expected 1, got 2"
  }

  test("uploadOnboardingDescriptorBackup fails verification when keyset ID missing") {
    val accountId = FullAccountId("test-account")
    val keyset = createFakeSpendingKeyset("test-keyset")

    ssekDao.set(SealedSsekFake, SsekFake)

    // Set up listKeysetsF8eClient to return backup with wrong keyset ID
    val wrongBackup = DescriptorBackup("wrong-keyset-id", XCiphertext("encrypted"), null)
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = listOf(
          LegacyRemoteKeyset(
            keysetId = keyset.f8eSpendingKeyset.keysetId,
            networkType = keyset.networkType.name,
            appDescriptor = keyset.appKey.key.dpub,
            hardwareDescriptor = keyset.hardwareKey.key.dpub,
            serverDescriptor = keyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
          )
        ),
        descriptorBackups = listOf(wrongBackup),
        wrappedSsek = SealedSsekFake
      )
    )

    val result = service.uploadOnboardingDescriptorBackup(
      accountId = accountId,
      sealedSsekForEncryption = SealedSsekFake,
      appAuthKey = PublicKey("app-auth-key"),
      keysetsToEncrypt = listOf(keyset)
    )

    result.shouldBeErrOfType<DescriptorBackupError.VerificationFailed>()
      .message shouldBe "Missing backup for keyset: test-keyset"
  }

  test("uploadOnboardingDescriptorBackup fails verification when SSEK doesn't match") {
    val accountId = FullAccountId("test-account")
    val keyset = createFakeSpendingKeyset("test-keyset")
    val differentSsek = "different-ssek".encodeUtf8()

    ssekDao.set(SealedSsekFake, SsekFake)

    // Set up listKeysetsF8eClient to return a different SSEK
    val encryptedBackup =
      service.sealDescriptors(SealedSsekFake, listOf(keyset)).getOrThrow().first()
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = listOf(
          LegacyRemoteKeyset(
            keysetId = keyset.f8eSpendingKeyset.keysetId,
            networkType = keyset.networkType.name,
            appDescriptor = keyset.appKey.key.dpub,
            hardwareDescriptor = keyset.hardwareKey.key.dpub,
            serverDescriptor = keyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
          )
        ),
        descriptorBackups = listOf(encryptedBackup),
        wrappedSsek = differentSsek
      )
    )

    val result = service.uploadOnboardingDescriptorBackup(
      accountId = accountId,
      sealedSsekForEncryption = SealedSsekFake,
      appAuthKey = PublicKey("app-auth-key"),
      keysetsToEncrypt = listOf(keyset)
    )

    result.shouldBeErrOfType<DescriptorBackupError.VerificationFailed>()
      .message shouldBe "Mismatch between provided and server-sealed SSEK during backup verification."
  }

  test("uploadOnboardingDescriptorBackup fails verification when server returns no SSEK") {
    val accountId = FullAccountId("test-account")
    val keyset = createFakeSpendingKeyset("test-keyset")

    ssekDao.set(SealedSsekFake, SsekFake)

    // Set up listKeysetsF8eClient to return no SSEK
    val encryptedBackup =
      service.sealDescriptors(SealedSsekFake, listOf(keyset)).getOrThrow().first()
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = listOf(
          LegacyRemoteKeyset(
            keysetId = keyset.f8eSpendingKeyset.keysetId,
            networkType = keyset.networkType.name,
            appDescriptor = keyset.appKey.key.dpub,
            hardwareDescriptor = keyset.hardwareKey.key.dpub,
            serverDescriptor = keyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
          )
        ),
        descriptorBackups = listOf(encryptedBackup),
        wrappedSsek = null
      )
    )

    val result = service.uploadOnboardingDescriptorBackup(
      accountId = accountId,
      sealedSsekForEncryption = SealedSsekFake,
      appAuthKey = PublicKey("app-auth-key"),
      keysetsToEncrypt = listOf(keyset)
    )

    result.shouldBeErrOfType<DescriptorBackupError.VerificationFailed>()
      .message shouldBe "No wrapped ssek found after upload"
  }

  test("uploadDescriptorBackups verifies uploaded backups successfully") {
    val accountId = FullAccountId("test-account")
    val existingKeyset = createFakeSpendingKeyset("existing-keyset")
    val newKeyset = createFakeSpendingKeyset("new-keyset")

    ssekDao.set(SealedSsekFake, SsekFake)

    // Encrypt the existing keyset
    val existingDescriptor = service.sealDescriptors(
      sealedSsek = SealedSsekFake,
      keysets = listOf(existingKeyset)
    ).getOrThrow().first()

    // Set up listKeysetsF8eClient to return both backups after upload
    val allKeysets = listOf(existingKeyset, newKeyset)
    val allDescriptors = service.sealDescriptors(SealedSsekFake, allKeysets).getOrThrow()
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = allKeysets.map { keyset ->

          LegacyRemoteKeyset(
            keysetId = keyset.f8eSpendingKeyset.keysetId,
            networkType = keyset.networkType.name,
            appDescriptor = keyset.appKey.key.dpub,
            hardwareDescriptor = keyset.hardwareKey.key.dpub,
            serverDescriptor = keyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
          )
        },
        descriptorBackups = allDescriptors,
        wrappedSsek = SealedSsekFake
      )
    )

    val result = service.uploadDescriptorBackups(
      accountId = accountId,
      sealedSsekForDecryption = SealedSsekFake,
      sealedSsekForEncryption = SealedSsekFake,
      appAuthKey = PublicKey("app-auth-key"),
      hwKeyProof = HwFactorProofOfPossession("hw-proof"),
      descriptorsToDecrypt = listOf(existingDescriptor),
      keysetsToEncrypt = listOf(newKeyset)
    )

    result.shouldBeOk()
  }

  test("uploadDescriptorBackups fails verification when descriptor content doesn't match") {
    val accountId = FullAccountId("test-account")
    val originalKeyset = createFakeSpendingKeyset("test-keyset")

    ssekDao.set(SealedSsekFake, SsekFake)

    // Create a tampered backup with different keys
    val tamperedKeyset = PrivateSpendingKeysetMock.copy(
      f8eSpendingKeyset = PrivateSpendingKeysetMock.f8eSpendingKeyset.copy(
        keysetId = "test-keyset"
      )
    )
    val tamperedDescriptor = service.sealDescriptors(
      sealedSsek = SealedSsekFake,
      keysets = listOf(tamperedKeyset)
    ).getOrThrow().first()

    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = listOf(
          LegacyRemoteKeyset(
            keysetId = originalKeyset.f8eSpendingKeyset.keysetId,
            networkType = originalKeyset.networkType.name,
            appDescriptor = originalKeyset.appKey.key.dpub,
            hardwareDescriptor = originalKeyset.hardwareKey.key.dpub,
            serverDescriptor = originalKeyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
          )
        ),
        descriptorBackups = listOf(tamperedDescriptor),
        wrappedSsek = SealedSsekFake
      )
    )

    val result = service.uploadOnboardingDescriptorBackup(
      accountId = accountId,
      sealedSsekForEncryption = SealedSsekFake,
      appAuthKey = PublicKey("app-auth-key"),
      keysetsToEncrypt = listOf(originalKeyset)
    )

    result.shouldBeErrOfType<DescriptorBackupError.VerificationFailed>()
      .message shouldBe "Descriptor keys mismatch for keyset test-keyset."
  }

  test("uploadOnboardingDescriptorBackup works for lite account upgrade") {
    accountConfigService.setActiveConfig(
      LiteAccountConfig(
        bitcoinNetworkType = BitcoinNetworkType.SIGNET,
        f8eEnvironment = Production,
        isTestAccount = false,
        isUsingSocRecFakes = false
      )
    )

    val accountId = FullAccountId("lite-account-upgrade")
    val keyset = createFakeLegacyRemoteKeyset("upgrade-keyset").toSpendingKeyset(uuidGenerator)
    val remoteKeyset = LegacyRemoteKeyset(
      keysetId = keyset.f8eSpendingKeyset.keysetId,
      networkType = keyset.networkType.name,
      appDescriptor = keyset.appKey.key.dpub,
      hardwareDescriptor = keyset.hardwareKey.key.dpub,
      serverDescriptor = keyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
    )

    ssekDao.set(SealedSsekFake, SsekFake)

    val encryptedBackup =
      service.sealDescriptors(SealedSsekFake, listOf(remoteKeyset).toSpendingKeysets(uuidGenerator)).getOrThrow().first()
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = listOf(remoteKeyset),
        descriptorBackups = listOf(encryptedBackup),
        wrappedSsek = SealedSsekFake
      )
    )

    val result = service.uploadOnboardingDescriptorBackup(
      accountId = accountId,
      sealedSsekForEncryption = SealedSsekFake,
      appAuthKey = PublicKey("app-auth-key"),
      keysetsToEncrypt = listOf(remoteKeyset).toSpendingKeysets(uuidGenerator)
    )

    result.shouldBeOk()
  }

  test("executeWork does not update cache when feature flag disabled") {
    accountService.accountState.value = Ok(ActiveAccount(FullAccountMock))

    service.executeWork()

    val activeKeysetId = FullAccountMock.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
    descriptorBackupVerificationDao.getVerifiedBackup(activeKeysetId)
      .get()
      .shouldBeNull()
  }

  test("executeWork stores verified backup in cache when descriptor found on F8e") {
    val keyset = privateAccount.keybox.activeSpendingKeyset
    val activeKeysetId = keyset.f8eSpendingKeyset.keysetId

    accountService.accountState.value = Ok(ActiveAccount(privateAccount))
    ssekDao.set(SealedSsekFake, SsekFake)

    val encryptedBackup = service.sealDescriptors(
      sealedSsek = SealedSsekFake,
      keysets = listOf(keyset)
    ).getOrThrow().first()

    val remoteKeyset = LegacyRemoteKeyset(
      keysetId = keyset.f8eSpendingKeyset.keysetId,
      networkType = keyset.networkType.name,
      appDescriptor = keyset.appKey.key.dpub,
      hardwareDescriptor = keyset.hardwareKey.key.dpub,
      serverDescriptor = keyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
    )

    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = listOf(remoteKeyset),
        descriptorBackups = listOf(encryptedBackup),
        wrappedSsek = SealedSsekFake
      )
    )

    service.executeWork()

    descriptorBackupVerificationDao.getVerifiedBackup(activeKeysetId)
      .get()
      .shouldNotBeNull()
  }

  test("executeWork does not call f8e when cache hits") {
    val activeKeysetId = privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId

    accountService.accountState.value = Ok(ActiveAccount(privateAccount))

    // Pre-populate cache
    descriptorBackupVerificationDao.replaceAllVerifiedBackups(
      listOf(VerifiedBackup(keysetId = activeKeysetId))
    )

    // F8e call would fail
    listKeysetsF8eClient.result = Err(NetworkError(cause = Exception("Network error")))

    service.executeWork()

    // Cache should remain unchanged
    descriptorBackupVerificationDao.getVerifiedBackup(activeKeysetId)
      .get()
      .shouldNotBeNull()
  }

  test("executeWork does not add to cache when F8e call fails and no cache exists") {
    val activeKeysetId = privateAccount.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId

    accountService.accountState.value = Ok(ActiveAccount(privateAccount))

    // No cached verification
    descriptorBackupVerificationDao.clear()

    // F8e call fails
    listKeysetsF8eClient.result = Err(NetworkError(cause = Exception("Network error")))

    service.executeWork()

    // Cache should remain empty
    descriptorBackupVerificationDao.getVerifiedBackup(activeKeysetId)
      .get()
      .shouldBeNull()
  }

  test("executeWork skips verification when active keyset is not private") {
    accountService.accountState.value = Ok(ActiveAccount(FullAccountMock))

    // Set up F8e client to fail - it should not be called
    listKeysetsF8eClient.result = Err(NetworkError(cause = Exception("Should not be called")))

    service.executeWork()

    // Cache should remain empty since we skipped verification
    val activeKeysetId = FullAccountMock.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId
    descriptorBackupVerificationDao.getVerifiedBackup(activeKeysetId)
      .get()
      .shouldBeNull()
  }

  test("parseDescriptorKeys parses valid descriptor with three keys") {
    val descriptorString = "wsh(sortedmulti(2," +
      "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK/*," +
      "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK/*," +
      "[34eae6a8/84'/0'/0']xpubDDj952KUFGTDcNV1qY5Tuevm6vnBWK8NSpTTkCz1XTApv2SeDaqcrUTBgDdCRF9KmtxV33R8E9NtSi9VSBUPj4M3fKr4uk3kRy8Vbo1LbAv/*))"
    val privateWalletRootXpub =
      "tpubD6NzVbkrYhZ4XPMXVToEroepyTscQmHYrdSDbvZvAFonusog8TjTB3iTQZ2Ds8atDfdxzN7DAioQ8Z4KBa4RD16FX7caE5hxiMbvkVr9Fom"

    val result = service.parseDescriptorKeys(
      descriptorString = descriptorString,
      privateWalletRootXpub = privateWalletRootXpub,
      keysetId = "test-keyset",
      networkType = BitcoinNetworkType.SIGNET
    )

    result.shouldBeOk()
    val keyset = result.value
    keyset.f8eSpendingKeyset.keysetId shouldBe "test-keyset"
    keyset.networkType shouldBe BitcoinNetworkType.SIGNET
    keyset.appKey.key.dpub shouldBe "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK/*"
    keyset.hardwareKey.key.dpub shouldBe "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK/*"
    keyset.f8eSpendingKeyset.spendingPublicKey.key.dpub shouldBe "[34eae6a8/84'/0'/0']xpubDDj952KUFGTDcNV1qY5Tuevm6vnBWK8NSpTTkCz1XTApv2SeDaqcrUTBgDdCRF9KmtxV33R8E9NtSi9VSBUPj4M3fKr4uk3kRy8Vbo1LbAv/*"
    keyset.f8eSpendingKeyset.privateWalletRootXpub shouldBe privateWalletRootXpub
  }

  test("parseDescriptorKeys returns error for invalid descriptor format missing wsh wrapper") {
    val invalidDescriptor = "sortedmulti(2,[key1],[key2],[key3])"

    val result = service.parseDescriptorKeys(
      descriptorString = invalidDescriptor,
      privateWalletRootXpub = null,
      keysetId = "test-keyset",
      networkType = BitcoinNetworkType.SIGNET
    )

    result.shouldBeErrOfType<DescriptorBackupError.DecryptionError>()
  }

  test("parseDescriptorKeys returns error for invalid descriptor format wrong threshold") {
    val invalidDescriptor = "wsh(sortedmulti(3,[key1],[key2],[key3]))"

    val result = service.parseDescriptorKeys(
      descriptorString = invalidDescriptor,
      privateWalletRootXpub = null,
      keysetId = "test-keyset",
      networkType = BitcoinNetworkType.SIGNET
    )

    result.shouldBeErrOfType<DescriptorBackupError.DecryptionError>()
  }

  test("parseDescriptorKeys returns error when descriptor has too few keys") {
    val invalidDescriptor = "wsh(sortedmulti(2,[key1],[key2]))"

    val result = service.parseDescriptorKeys(
      descriptorString = invalidDescriptor,
      privateWalletRootXpub = null,
      keysetId = "test-keyset",
      networkType = BitcoinNetworkType.SIGNET
    )

    result.shouldBeErrOfType<DescriptorBackupError.DecryptionError>()
  }

  test("parseDescriptorKeys returns error when descriptor has too many keys") {
    val invalidDescriptor = "wsh(sortedmulti(2,[key1],[key2],[key3],[key4]))"

    val result = service.parseDescriptorKeys(
      descriptorString = invalidDescriptor,
      privateWalletRootXpub = null,
      keysetId = "test-keyset",
      networkType = BitcoinNetworkType.SIGNET
    )

    result.shouldBeErrOfType<DescriptorBackupError.DecryptionError>()
  }

  test("parseDescriptorKeys returns error for malformed descriptor missing closing parenthesis") {
    val invalidDescriptor = "wsh(sortedmulti(2,[key1],[key2],[key3])"

    val result = service.parseDescriptorKeys(
      descriptorString = invalidDescriptor,
      privateWalletRootXpub = null,
      keysetId = "test-keyset",
      networkType = BitcoinNetworkType.SIGNET
    )

    result.shouldBeErrOfType<DescriptorBackupError.DecryptionError>()
  }

  test("prepareDescriptorBackupsForRecovery for lost hardware filters out private keysets without descriptor backups") {
    val accountId = FullAccountId("test-account")
    val newKeyset = createFakeSpendingKeyset("new-keyset")

    // Create a mix of legacy and private keysets
    val legacyKeyset = createFakeLegacyRemoteKeyset("legacy-keyset")
    val privateKeyset = PrivateMultisigRemoteKeyset(
      keysetId = "private-keyset",
      networkType = "SIGNET",
      appPublicKey = "private-app-pub",
      hardwarePublicKey = "private-hardware-pub",
      serverPublicKey = "private-server-pub"
    )
    val newKeysetRemote = LegacyRemoteKeyset(
      keysetId = newKeyset.f8eSpendingKeyset.keysetId,
      networkType = newKeyset.networkType.name,
      appDescriptor = newKeyset.appKey.key.dpub,
      hardwareDescriptor = newKeyset.hardwareKey.key.dpub,
      serverDescriptor = newKeyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
    )
    val remoteKeysets: List<RemoteKeyset> = listOf(legacyKeyset, privateKeyset, newKeysetRemote)

    accountService.accountState.value = Ok(
      ActiveAccount(
        FullAccountMock.copy(
          keybox = FullAccountMock.keybox.copy(canUseKeyboxKeysets = false)
        )
      )
    )

    // F8e returns keysets but no descriptor backups
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = remoteKeysets,
        descriptorBackups = emptyList(),
        wrappedSsek = null
      )
    )

    val result = service.prepareDescriptorBackupsForRecovery(
      accountId = accountId,
      factorToRecover = PhysicalFactor.Hardware,
      f8eSpendingKeyset = newKeyset.f8eSpendingKeyset,
      appSpendingKey = newKeyset.appKey,
      hwSpendingKey = newKeyset.hardwareKey
    ).get()

    val encryptOnlyResult =
      result.shouldNotBeNull().shouldBeInstanceOf<DescriptorBackupPreparedData.EncryptOnly>()

    // Should only include legacy keysets and the new keyset, private keyset filtered out
    encryptOnlyResult.keysetsToEncrypt.size shouldBe 2
    encryptOnlyResult.keysetsToEncrypt.map { it.f8eSpendingKeyset.keysetId }
      .shouldContainExactly(listOf("legacy-keyset", "new-keyset"))
  }

  test("prepareDescriptorBackupsForRecovery for lost app filters out private keysets without descriptor backups") {
    val accountId = FullAccountId("test-account")
    val newKeyset = createFakeSpendingKeyset("new-keyset")

    // Create a mix of legacy and private keysets
    val legacyKeyset1 = createFakeLegacyRemoteKeyset("legacy-keyset-1")
    val privateKeyset = PrivateMultisigRemoteKeyset(
      keysetId = "private-keyset",
      networkType = "SIGNET",
      appPublicKey = "private-app-pub",
      hardwarePublicKey = "private-hardware-pub",
      serverPublicKey = "private-server-pub"
    )
    val legacyKeyset2 = createFakeLegacyRemoteKeyset("legacy-keyset-2")
    val newKeysetRemote = LegacyRemoteKeyset(
      keysetId = newKeyset.f8eSpendingKeyset.keysetId,
      networkType = newKeyset.networkType.name,
      appDescriptor = newKeyset.appKey.key.dpub,
      hardwareDescriptor = newKeyset.hardwareKey.key.dpub,
      serverDescriptor = newKeyset.f8eSpendingKeyset.spendingPublicKey.key.dpub
    )
    val remoteKeysets: List<RemoteKeyset> =
      listOf(legacyKeyset1, privateKeyset, legacyKeyset2, newKeysetRemote)

    // F8e returns keysets but no descriptor backups
    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = remoteKeysets,
        descriptorBackups = emptyList(),
        wrappedSsek = null
      )
    )

    val result = service.prepareDescriptorBackupsForRecovery(
      accountId = accountId,
      factorToRecover = PhysicalFactor.App,
      f8eSpendingKeyset = newKeyset.f8eSpendingKeyset,
      appSpendingKey = newKeyset.appKey,
      hwSpendingKey = newKeyset.hardwareKey
    ).get()

    val encryptOnlyResult =
      result.shouldNotBeNull().shouldBeInstanceOf<DescriptorBackupPreparedData.EncryptOnly>()

    // Should only include legacy keysets and the new keyset, private keyset filtered out
    encryptOnlyResult.keysetsToEncrypt.size shouldBe 3
    encryptOnlyResult.keysetsToEncrypt.map { it.f8eSpendingKeyset.keysetId }
      .shouldContainExactly(listOf("legacy-keyset-1", "legacy-keyset-2", "new-keyset"))
  }

  test("checkBackupForPrivateKeyset returns Ok when feature flag is disabled") {
    descriptorBackupFailsafeFeatureFlag.setFlagValue(BooleanFlag(false))
    val privateKeyset = PrivateSpendingKeysetMock

    val result = service.checkBackupForPrivateKeyset(privateKeyset.f8eSpendingKeyset.keysetId)

    result.shouldBeOk()
  }

  test("checkBackupForPrivateKeyset returns Ok when backup exists in cache") {
    descriptorBackupFailsafeFeatureFlag.setFlagValue(BooleanFlag(true))
    val privateKeyset = PrivateSpendingKeysetMock
    val keysetId = privateKeyset.f8eSpendingKeyset.keysetId

    // Pre-populate cache with backup
    descriptorBackupVerificationDao.replaceAllVerifiedBackups(
      listOf(VerifiedBackup(keysetId = keysetId))
    )

    val result = service.checkBackupForPrivateKeyset(keysetId)

    result.shouldBeOk()
  }

  test("checkBackupForPrivateKeyset returns error when private keyset has no backup in cache") {
    descriptorBackupFailsafeFeatureFlag.setFlagValue(BooleanFlag(true))
    val privateKeyset = PrivateSpendingKeysetMock

    // Cache is empty, no backup exists
    descriptorBackupVerificationDao.clear()

    val result = service.checkBackupForPrivateKeyset(privateKeyset.f8eSpendingKeyset.keysetId)

    result.shouldBeErrOfType<IllegalStateException>()
  }
})
