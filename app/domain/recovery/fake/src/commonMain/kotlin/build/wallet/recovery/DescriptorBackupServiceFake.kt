package build.wallet.recovery

import bitkey.backup.DescriptorBackup
import bitkey.recovery.DescriptorBackupError
import bitkey.recovery.DescriptorBackupPreparedData
import bitkey.recovery.DescriptorBackupService
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XCiphertext
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.LegacyRemoteKeyset
import build.wallet.recovery.DescriptorBackupServiceFake.Companion.HW_DESCRIPTOR_PUBKEY
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class DescriptorBackupServiceFake : DescriptorBackupService {
  companion object {
    val HW_DESCRIPTOR_PUBKEY =
      "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK/*"
  }

  val fakeKeysetList = listOf(SpendingKeysetMock)
  var prepareDescriptorBackupsForRecoveryResult: Result<DescriptorBackupPreparedData, Error> = Ok(
    DescriptorBackupPreparedData.EncryptOnly(fakeKeysetList)
  )
  var uploadOnboardingDescriptorBackupResult: Result<Unit, DescriptorBackupError> = Ok(Unit)
  var uploadDescriptorBackupsResult: Result<List<SpendingKeyset>, DescriptorBackupError> =
    Ok(fakeKeysetList)
  var checkBackupForPrivateKeysetResult: Result<Unit, Throwable> = Ok(Unit)

  private val sealedDescriptorsStorage = mutableMapOf<String, XCiphertext>()

  override suspend fun checkBackupForPrivateKeyset(keysetId: String): Result<Unit, Throwable> {
    return checkBackupForPrivateKeysetResult
  }

  override suspend fun prepareDescriptorBackupsForRecovery(
    accountId: FullAccountId,
    factorToRecover: PhysicalFactor,
    f8eSpendingKeyset: F8eSpendingKeyset,
    appSpendingKey: AppSpendingPublicKey,
    hwSpendingKey: HwSpendingPublicKey,
  ): Result<DescriptorBackupPreparedData, Error> {
    return prepareDescriptorBackupsForRecoveryResult
  }

  override suspend fun uploadOnboardingDescriptorBackup(
    accountId: FullAccountId,
    sealedSsekForEncryption: SealedSsek,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    keysetsToEncrypt: List<SpendingKeyset>,
  ): Result<Unit, DescriptorBackupError> {
    return uploadOnboardingDescriptorBackupResult
  }

  override suspend fun uploadDescriptorBackups(
    accountId: FullAccountId,
    sealedSsekForDecryption: SealedSsek?,
    sealedSsekForEncryption: SealedSsek,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hwKeyProof: HwFactorProofOfPossession,
    descriptorsToDecrypt: List<DescriptorBackup>,
    keysetsToEncrypt: List<SpendingKeyset>,
  ): Result<List<SpendingKeyset>, DescriptorBackupError> {
    return uploadDescriptorBackupsResult
  }

  override suspend fun sealDescriptors(
    sealedSsek: SealedSsek,
    keysets: List<SpendingKeyset>,
  ): Result<List<DescriptorBackup>, DescriptorBackupError.SsekNotFound> {
    // Create fake sealed descriptors
    val descriptorBackups = keysets.map { keyset ->
      val fakeSealed = XCiphertext("fake-sealed-${keyset.f8eSpendingKeyset.keysetId}")
      val fakeSealedPrivateWalletXpub = XCiphertext("fake-sealed-private-wallet-xpub-${keyset.f8eSpendingKeyset.keysetId}")
      sealedDescriptorsStorage[keyset.f8eSpendingKeyset.keysetId] = fakeSealed

      DescriptorBackup(
        keysetId = keyset.f8eSpendingKeyset.keysetId,
        sealedDescriptor = fakeSealed,
        privateWalletRootXpub = fakeSealedPrivateWalletXpub
      )
    }

    return Ok(descriptorBackups)
  }

  override suspend fun unsealDescriptors(
    sealedSsek: SealedSsek,
    encryptedDescriptorBackups: List<DescriptorBackup>,
  ): Result<List<SpendingKeyset>, DescriptorBackupError> {
    // Create fake "decrypted" keysets for testing
    // In reality, this would decrypt the descriptor and reconstruct the actual keysets
    val fakeKeysets = encryptedDescriptorBackups.map { backup ->
      createFakeSpendingKeyset(backup.keysetId)
    }

    return Ok(fakeKeysets)
  }

  override suspend fun parseDescriptorKeys(
    descriptorString: String,
    privateWalletRootXpub: String?,
    keysetId: String,
    networkType: BitcoinNetworkType,
  ): Result<SpendingKeyset, DescriptorBackupError> {
    return Ok(SpendingKeysetMock)
  }

  // Reset methods for testing
  fun reset() {
    sealedDescriptorsStorage.clear()
    prepareDescriptorBackupsForRecoveryResult = Ok(
      DescriptorBackupPreparedData.EncryptOnly(fakeKeysetList)
    )
    uploadOnboardingDescriptorBackupResult = Ok(Unit)
    uploadDescriptorBackupsResult = Ok(fakeKeysetList)
    checkBackupForPrivateKeysetResult = Ok(Unit)
  }
}

fun createFakeSpendingKeyset(
  keysetId: String,
  localId: String = "abc123",
  privateWalletRootXpub: String? =
    "tpubD6NzVbkrYhZ4XPMXVToEroepyTscQmHYrdSDbvZvAFonusog8TjTB3iTQZ2Ds8atDfdxzN7DAioQ8Z4KBa4RD16FX7caE5hxiMbvkVr9Fom",
): SpendingKeyset {
  // This is a simplified fake keyset for testing purposes
  // Real implementation would reconstruct from decrypted descriptor
  return SpendingKeyset(
    localId = localId,
    f8eSpendingKeyset = F8eSpendingKeyset(
      keysetId = keysetId,
      spendingPublicKey = F8eSpendingPublicKey(
        DescriptorPublicKey("[34eae6a8/84'/0'/0']xpubDDj952KUFGTDcNV1qY5Tuevm6vnBWK8NSpTTkCz1XTApv2SeDaqcrUTBgDdCRF9KmtxV33R8E9NtSi9VSBUPj4M3fKr4uk3kRy8Vbo1LbAv/*")
      ),
      privateWalletRootXpub = privateWalletRootXpub
    ),
    networkType = build.wallet.bitcoin.BitcoinNetworkType.SIGNET,
    appKey = AppSpendingPublicKey(
      DescriptorPublicKey("[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK/*")
    ),
    hardwareKey = HwSpendingPublicKey(
      DescriptorPublicKey(HW_DESCRIPTOR_PUBKEY)
    )
  )
}

fun createFakeLegacyRemoteKeyset(keysetId: String): LegacyRemoteKeyset =
  LegacyRemoteKeyset(
    keysetId = keysetId,
    networkType = "SIGNET",
    appDescriptor = "[e5ff120e/84'/0'/0']xpub6Gxgx4jtKP3xsM95Rtub11QE4YqGDxTw9imtJ23Bi7nFi2aqE27HwanX2x3m451zuni5tKSuHeFVHexyCkjDEwB74R7NRtQ2UryVKDy1fgK/*",
    hardwareDescriptor = HW_DESCRIPTOR_PUBKEY,
    serverDescriptor = "[34eae6a8/84'/0'/0']xpubDDj952KUFGTDcNV1qY5Tuevm6vnBWK8NSpTTkCz1XTApv2SeDaqcrUTBgDdCRF9KmtxV33R8E9NtSi9VSBUPj4M3fKr4uk3kRy8Vbo1LbAv/*"
  )
