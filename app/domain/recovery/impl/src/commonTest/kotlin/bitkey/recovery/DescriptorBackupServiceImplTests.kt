package bitkey.recovery

import bitkey.account.AccountConfigServiceFake
import bitkey.backup.DescriptorBackup
import bitkey.f8e.account.UpdateDescriptorBackupsF8eClientFake
import bitkey.recovery.DescriptorBackupError.SsekNotFound
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderImpl
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.cloud.backup.csek.SsekDaoFake
import build.wallet.cloud.backup.csek.SsekFake
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.SymmetricKeyEncryptorFake
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.ListKeysetsF8eClient.ListKeysetsResponse
import build.wallet.f8e.recovery.ListKeysetsF8eClientMock
import build.wallet.platform.random.UuidGeneratorFake
import build.wallet.recovery.createFakeSpendingKeyset
import build.wallet.testing.shouldBeErr
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class DescriptorBackupServiceImplTests : FunSpec({
  val ssekDao = SsekDaoFake()
  val symmetricKeyEncryptor = SymmetricKeyEncryptorFake()
  val uuidGenerator = UuidGeneratorFake()
  val accountConfigService = AccountConfigServiceFake()
  val listKeysetsF8eClient = ListKeysetsF8eClientMock()
  val accountService = AccountServiceFake()
  val service = DescriptorBackupServiceImpl(
    ssekDao = ssekDao,
    symmetricKeyEncryptor = symmetricKeyEncryptor,
    accountConfigService = accountConfigService,
    uuidGenerator = uuidGenerator,
    bitcoinMultiSigDescriptorBuilder = BitcoinMultiSigDescriptorBuilderImpl(),
    listKeysetsF8eClient = listKeysetsF8eClient,
    updateDescriptorBackupsF8eClient = UpdateDescriptorBackupsF8eClientFake(),
    accountService = accountService
  )

  beforeTest {
    uuidGenerator.reset()
    symmetricKeyEncryptor.reset()
    ssekDao.reset()
    accountConfigService.reset()
    listKeysetsF8eClient.reset()
    accountService.reset()
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

    val unsealResult = service.unsealDescriptors(SealedSsekFake, encryptedDescriptors)
    val restoredKeysets = unsealResult.get().shouldNotBeNull()

    restoredKeysets.size shouldBe 2

    // Verify first keyset
    restoredKeysets[0].apply {
      f8eSpendingKeyset.keysetId shouldBe "keyset-1"
      appKey.key shouldBe appSpendingKey
      hardwareKey.key shouldBe hwSpendingKey
      f8eSpendingKeyset.spendingPublicKey shouldBe f8eSpendingKey
    }

    // Verify second keyset
    with(restoredKeysets[1]) {
      f8eSpendingKeyset.keysetId shouldBe "keyset-2"
      appKey.key shouldBe appSpendingKey
      hardwareKey.key shouldBe hwSpendingKey
      f8eSpendingKeyset.spendingPublicKey shouldBe f8eSpendingKey
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
      sealedDescriptor = XCiphertext("xciphertext")
    )

    service.unsealDescriptors(SealedSsekFake, listOf(encryptedBackup))
      .shouldBeErr(SsekNotFound)
  }

  test("prepareDescriptorBackupsForRecovery for hardware recovery with incomplete local keysets") {
    val accountId = FullAccountId("test-account")
    val newKeyset = createFakeSpendingKeyset("new-keyset")
    val f8eKeysets = listOf(createFakeSpendingKeyset("existing-keyset"))

    accountService.accountState.value = Ok(
      ActiveAccount(
        FullAccountMock.copy(
          keybox = FullAccountMock.keybox.copy(canUseKeyboxKeysets = false)
        )
      )
    )

    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = f8eKeysets,
        descriptorBackups = null,
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

    result.shouldNotBeNull().shouldBeInstanceOf<DescriptorBackupPreparedData.EncryptOnly>()
      .keysetsToEncrypt shouldContainExactly f8eKeysets
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
    val existingDescriptors = listOf(
      DescriptorBackup(
        keysetId = "existing-keyset",
        sealedDescriptor = XCiphertext("encrypted")
      )
    )

    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = emptyList(),
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
        keysetsToEncrypt[0].f8eSpendingKeyset.keysetId shouldBe "new-keyset"
        sealedSsek shouldBe SealedSsekFake
      }
  }

  test("prepareDescriptorBackupsForRecovery for app recovery with available SSEK") {
    val accountId = FullAccountId("test-account")
    val newKeyset = createFakeSpendingKeyset("new-keyset")
    val existingDescriptors = listOf(
      DescriptorBackup(
        keysetId = "existing-keyset",
        sealedDescriptor = XCiphertext("encrypted")
      )
    )

    // Set up SSEK in storage
    ssekDao.set(SealedSsekFake, SsekFake)

    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = emptyList(),
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
      keysetsToEncrypt[0].f8eSpendingKeyset.keysetId shouldBe "new-keyset"
      sealedSsek shouldBe SealedSsekFake
    }
  }

  test("prepareDescriptorBackupsForRecovery for app recovery with no descriptor backups") {
    val accountId = FullAccountId("test-account")
    val newKeyset = createFakeSpendingKeyset("new-keyset")
    val f8eKeysets = listOf(createFakeSpendingKeyset("f8e-keyset"))

    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = f8eKeysets,
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

    result.shouldNotBeNull().shouldBeInstanceOf<DescriptorBackupPreparedData.EncryptOnly>()
      .keysetsToEncrypt shouldContainExactly f8eKeysets
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
})
