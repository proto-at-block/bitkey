package bitkey.recovery

import bitkey.account.AccountConfigServiceFake
import bitkey.backup.DescriptorBackup
import bitkey.f8e.account.UpdateDescriptorBackupsF8eClientFake
import bitkey.recovery.DescriptorBackupError.SsekNotFound
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilderImpl
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.spending.SpendingKeyset
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
import build.wallet.testing.shouldBeErr
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import okio.ByteString.Companion.encodeUtf8

class DescriptorBackupServiceImplTests : FunSpec({
  val appSpendingKey =
    "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK/*"
  val hwSpendingKey =
    "[deadbeef/84'/0'/0']xpub6ERApfZwUNrhLCkDtcHTcxd75RbzS1ed54G1LkBUHQVHQKqhMkhgbmJbZRkrgZw4koxb5JaHWkY4ALHY2grBGRjaDMzQLcgJvLJuZZvRcEL/*"
  val f8eSpendingKey =
    "[34eae6a8/84'/0'/0']xpubDDj952KUFGTDcNV1qY5Tuevm6vnBWK8NSpTTkCz1XTApv2SeDaqcrUTBgDdCRF9KmtxV33R8E9NtSi9VSBUPj4M3fKr4uk3kRy8Vbo1LbAv/*"

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

  fun createTestKeyset(keysetId: String) =
    SpendingKeyset(
      localId = uuidGenerator.random(),
      networkType = accountConfigService.defaultConfig().value.bitcoinNetworkType,
      appKey = AppSpendingPublicKey(DescriptorPublicKey(appSpendingKey)),
      hardwareKey = HwSpendingPublicKey(DescriptorPublicKey(hwSpendingKey)),
      f8eSpendingKeyset = F8eSpendingKeyset(
        keysetId = keysetId,
        spendingPublicKey = F8eSpendingPublicKey(DescriptorPublicKey(f8eSpendingKey))
      )
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
      createTestKeyset("keyset-1"),
      createTestKeyset("keyset-2")
    )

    val encryptedDescriptors =
      service.sealDescriptors(SealedSsekFake, originalKeysets).get().shouldNotBeNull()
    encryptedDescriptors.map { it.keysetId }.shouldContainExactly("keyset-1", "keyset-2")

    val unsealResult = service.unsealDescriptors(SealedSsekFake, encryptedDescriptors)
    val restoredKeysets = unsealResult.get().shouldNotBeNull()

    restoredKeysets.size shouldBe 2

    // Verify first keyset
    restoredKeysets[0].apply {
      f8eSpendingKeyset.keysetId shouldBe "keyset-1"
      appKey.key shouldBe DescriptorPublicKey(appSpendingKey)
      hardwareKey.key shouldBe DescriptorPublicKey(hwSpendingKey)
      f8eSpendingKeyset.spendingPublicKey.key shouldBe DescriptorPublicKey(f8eSpendingKey)
    }

    // Verify second keyset
    with(restoredKeysets[1]) {
      f8eSpendingKeyset.keysetId shouldBe "keyset-2"
      appKey.key shouldBe DescriptorPublicKey(appSpendingKey)
      hardwareKey.key shouldBe DescriptorPublicKey(hwSpendingKey)
      f8eSpendingKeyset.spendingPublicKey.key shouldBe DescriptorPublicKey(f8eSpendingKey)
    }
  }

  test("sealDescriptors returns error when SSEK is not found") {
    service.sealDescriptors(
      sealedSsek = SealedSsekFake,
      keysets = listOf(createTestKeyset("keyset-1"))
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

  test("prepareDescriptorBackupsForRecovery for hardware recovery with no local keysets") {
    val accountId = FullAccountId("test-account")
    val newKeyset = createTestKeyset("new-keyset")
    val f8eKeysets = listOf(createTestKeyset("existing-keyset"))

    accountService.accountState.value = Ok(ActiveAccount(FullAccountMock))

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

  test("prepareDescriptorBackupsForRecovery for app recovery with existing descriptors") {
    val accountId = FullAccountId("test-account")
    val newKeyset = createTestKeyset("new-keyset")
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
        wrappedSsek = "wrapped-ssek"
      )
    )

    val result = service.prepareDescriptorBackupsForRecovery(
      accountId = accountId,
      factorToRecover = PhysicalFactor.App,
      f8eSpendingKeyset = newKeyset.f8eSpendingKeyset,
      appSpendingKey = newKeyset.appKey,
      hwSpendingKey = newKeyset.hardwareKey
    ).get()

    result.shouldNotBeNull().shouldBeInstanceOf<DescriptorBackupPreparedData.NeedsUnsealed>().apply {
      descriptorsToDecrypt shouldContainExactly existingDescriptors
      keysetsToEncrypt.size shouldBe 1
      keysetsToEncrypt[0].f8eSpendingKeyset.keysetId shouldBe "new-keyset"
      sealedSsek shouldBe "wrapped-ssek".encodeUtf8()
    }
  }

  test("prepareDescriptorBackupsForRecovery for app recovery with available SSEK") {
    val accountId = FullAccountId("test-account")
    val newKeyset = createTestKeyset("new-keyset")
    val existingDescriptors = listOf(
      DescriptorBackup(
        keysetId = "existing-keyset",
        sealedDescriptor = XCiphertext("encrypted")
      )
    )

    // Set up SSEK in storage
    val wrappedSsekString = SealedSsekFake.hex()
    val wrappedSsekBytes = wrappedSsekString.encodeUtf8()
    ssekDao.set(wrappedSsekBytes, SsekFake)

    listKeysetsF8eClient.result = Ok(
      ListKeysetsResponse(
        keysets = emptyList(),
        descriptorBackups = existingDescriptors,
        wrappedSsek = wrappedSsekString
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
      sealedSsek shouldBe wrappedSsekBytes
    }
  }

  test("prepareDescriptorBackupsForRecovery for app recovery with no descriptor backups") {
    val accountId = FullAccountId("test-account")
    val newKeyset = createTestKeyset("new-keyset")
    val f8eKeysets = listOf(createTestKeyset("f8e-keyset"))

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
    val existingKeyset = createTestKeyset("existing-keyset")
    val newKeyset = createTestKeyset("new-keyset")

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
