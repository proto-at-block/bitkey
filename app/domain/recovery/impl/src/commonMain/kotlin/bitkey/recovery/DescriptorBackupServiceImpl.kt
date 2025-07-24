package bitkey.recovery

import bitkey.account.AccountConfigService
import bitkey.account.DefaultAccountConfig
import bitkey.account.FullAccountConfig
import bitkey.account.LiteAccountConfig
import bitkey.account.SoftwareAccountConfig
import bitkey.backup.DescriptorBackup
import bitkey.f8e.account.UpdateDescriptorBackupsF8eClient
import bitkey.recovery.DescriptorBackupError.DecryptionError
import bitkey.recovery.DescriptorBackupError.SsekNotFound
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.cloud.backup.csek.SsekDao
import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SymmetricKeyEncryptor
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.logging.logDebug
import build.wallet.logging.logError
import build.wallet.platform.random.UuidGenerator
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import okio.ByteString.Companion.encodeUtf8

@BitkeyInject(AppScope::class)
class DescriptorBackupServiceImpl(
  private val ssekDao: SsekDao,
  private val symmetricKeyEncryptor: SymmetricKeyEncryptor,
  private val accountConfigService: AccountConfigService,
  private val uuidGenerator: UuidGenerator,
  private val bitcoinMultiSigDescriptorBuilder: BitcoinMultiSigDescriptorBuilder,
  private val listKeysetsF8eClient: ListKeysetsF8eClient,
  private val updateDescriptorBackupsF8eClient: UpdateDescriptorBackupsF8eClient,
  private val accountService: AccountService,
) : DescriptorBackupService {
  private companion object {
    /**
     * Regex pattern for parsing Bitcoin descriptor strings.
     * Format: wsh(sortedmulti(2,key1,key2,key3))
     * Groups:
     * 1. The entire key list (key1,key2,key3)
     * 2. Individual keys in a non-capturing group
     */
    private val DESCRIPTOR_PATTERN = Regex("""wsh\(sortedmulti\(2,((?:[^,]+,){2}[^,]+)\)\)""")
  }

  private val descriptorBackupAad = "Bitkey Descriptor Backup Encryption Version 1.0".encodeUtf8()

  override suspend fun prepareDescriptorBackupsForRecovery(
    accountId: FullAccountId,
    factorToRecover: PhysicalFactor,
    f8eSpendingKeyset: F8eSpendingKeyset,
    appSpendingKey: AppSpendingPublicKey,
    hwSpendingKey: HwSpendingPublicKey,
  ): Result<DescriptorBackupPreparedData, Error> {
    return coroutineBinding {
      val accountConfig = getFullAccountConfig()
      val newActiveKeyset = createNewActiveKeyset(
        f8eSpendingKeyset = f8eSpendingKeyset,
        networkType = accountConfig.bitcoinNetworkType,
        appSpendingKey = appSpendingKey,
        hwSpendingKey = hwSpendingKey
      )

      when (factorToRecover) {
        PhysicalFactor.Hardware -> prepareForHardwareRecovery(
          accountId = accountId,
          accountConfig = accountConfig,
          newActiveKeyset = newActiveKeyset
        ).bind()

        PhysicalFactor.App -> prepareForAppRecovery(
          accountId = accountId,
          accountConfig = accountConfig,
          newActiveKeyset = newActiveKeyset
        ).bind()
      }
    }
  }

  override suspend fun uploadOnboardingDescriptorBackup(
    accountId: FullAccountId,
    sealedSsekForEncryption: SealedSsek,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    keysetsToEncrypt: List<SpendingKeyset>,
  ): Result<Unit, DescriptorBackupError> =
    coroutineBinding {
      require(keysetsToEncrypt.size == 1) {
        "must have keysets to encrypt, but had none"
      }

      logDebug { "Uploading initial descriptor backups for account $accountId" }

      // Seal the descriptors using the provided SSEK
      val encryptedDescriptors = sealDescriptors(
        sealedSsek = sealedSsekForEncryption,
        keysets = keysetsToEncrypt
      ).bind()

      // Upload the encrypted descriptors to F8e
      uploadBackupsToF8e(
        accountId = accountId,
        sealedSsek = sealedSsekForEncryption,
        descriptorBackups = encryptedDescriptors,
        appAuthKey = appAuthKey,
        hwKeyProof = null
      ).bind()
    }

  override suspend fun uploadDescriptorBackups(
    accountId: FullAccountId,
    sealedSsekForDecryption: SealedSsek?,
    sealedSsekForEncryption: SealedSsek,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hwKeyProof: HwFactorProofOfPossession,
    descriptorsToDecrypt: List<DescriptorBackup>,
    keysetsToEncrypt: List<SpendingKeyset>,
  ): Result<List<SpendingKeyset>, DescriptorBackupError> =
    coroutineBinding {
      val processedResult = processDescriptorBackupsForRecovery(
        descriptorsToDecrypt = descriptorsToDecrypt,
        keysetsToEncrypt = keysetsToEncrypt,
        sealedSsekToEncrypt = sealedSsekForEncryption,
        sealedSsekToDecrypt = sealedSsekForDecryption
      ).bind()

      uploadBackupsToF8e(
        accountId = accountId,
        descriptorBackups = processedResult.encryptedDescriptors,
        sealedSsek = sealedSsekForEncryption,
        appAuthKey = appAuthKey,
        hwKeyProof = hwKeyProof
      ).bind()

      processedResult.allKeysets
    }

  override suspend fun sealDescriptors(
    sealedSsek: SealedSsek,
    keysets: List<SpendingKeyset>,
  ): Result<List<DescriptorBackup>, SsekNotFound> {
    return coroutineBinding {
      val ssek = ssekDao.get(sealedSsek).get() ?: Err(SsekNotFound).bind()

      keysets.map { keyset ->
        val watchingDescriptor = bitcoinMultiSigDescriptorBuilder.watchingDescriptor(
          appPublicKey = keyset.appKey.key,
          hardwareKey = keyset.hardwareKey.key,
          serverKey = keyset.f8eSpendingKeyset.spendingPublicKey.key
        )

        val encryptedDescriptor = symmetricKeyEncryptor.seal(
          unsealedData = watchingDescriptor.raw.encodeUtf8(),
          key = ssek.key,
          aad = descriptorBackupAad
        )

        DescriptorBackup(
          keysetId = keyset.f8eSpendingKeyset.keysetId,
          sealedDescriptor = encryptedDescriptor
        )
      }
    }
  }

  override suspend fun unsealDescriptors(
    sealedSsek: SealedSsek,
    encryptedDescriptorBackups: List<DescriptorBackup>,
  ): Result<List<SpendingKeyset>, DescriptorBackupError> {
    return coroutineBinding {
      val ssek = ssekDao.get(sealedSsek).get() ?: Err(SsekNotFound).bind()
      val accountConfig = getFullAccountConfig()

      encryptedDescriptorBackups.map { encryptedBackup ->
        // Decrypt the descriptor
        val decryptedDescriptorData = symmetricKeyEncryptor.unseal(
          ciphertext = encryptedBackup.sealedDescriptor,
          key = ssek.key,
          aad = descriptorBackupAad
        ).utf8()

        // Parse the descriptor and convert to SpendingKeyset
        parseDescriptorKeys(
          descriptorString = decryptedDescriptorData,
          keysetId = encryptedBackup.keysetId,
          networkType = accountConfig.bitcoinNetworkType
        ).bind()
      }
    }
  }

  private fun getFullAccountConfig(): FullAccountConfig =
    when (val config = accountConfigService.activeOrDefaultConfig().value) {
      is DefaultAccountConfig -> config.toFullAccountConfig()
      is FullAccountConfig -> config
      is LiteAccountConfig -> error("Lite account config is not supported")
      is SoftwareAccountConfig -> error("Software account config is not supported")
    }

  private fun createNewActiveKeyset(
    f8eSpendingKeyset: F8eSpendingKeyset,
    networkType: BitcoinNetworkType,
    appSpendingKey: AppSpendingPublicKey,
    hwSpendingKey: HwSpendingPublicKey,
  ) = SpendingKeyset(
    localId = uuidGenerator.random(),
    f8eSpendingKeyset = f8eSpendingKeyset,
    networkType = networkType,
    appKey = appSpendingKey,
    hardwareKey = hwSpendingKey
  )

  private suspend fun prepareForHardwareRecovery(
    accountId: FullAccountId,
    accountConfig: FullAccountConfig,
    newActiveKeyset: SpendingKeyset,
  ): Result<DescriptorBackupPreparedData, DescriptorBackupError> =
    coroutineBinding {
      logDebug { "Preparing descriptor backups for Lost Hardware recovery" }

      val keybox = accountService.getAccount<FullAccount>()
        .mapError { DescriptorBackupError.AccountNotFound }
        .map { it.keybox }
        .bind()

      val allKeysets = if (!keybox.canUseKeyboxKeysets) {
        logDebug { "Retrieving f8e keysets for Lost Hardware recovery" }
        // If the local keysets are not authoritative, we need to retrieve keysets
        // from f8e.
        listKeysetsF8eClient.listKeysets(
          f8eEnvironment = accountConfig.f8eEnvironment,
          fullAccountId = accountId
        ).onFailure { logError { "Failed to list keysets from F8e: $it" } }
          .mapError { DescriptorBackupError.NetworkError(it) }
          .bind().keysets
      } else {
        // Otherwise, we take our local keysets and append the new one
        logDebug { "Using local keysets Lost Hardware recovery" }
        keybox.keysets + newActiveKeyset
      }

      DescriptorBackupPreparedData.EncryptOnly(
        keysetsToEncrypt = allKeysets
      )
    }

  private suspend fun prepareForAppRecovery(
    accountId: FullAccountId,
    accountConfig: FullAccountConfig,
    newActiveKeyset: SpendingKeyset,
  ): Result<DescriptorBackupPreparedData, DescriptorBackupError> =
    coroutineBinding {
      logDebug { "Preparing descriptor backups for Lost App and Cloud recovery" }

      val listKeysetsResponse = listKeysetsF8eClient.listKeysets(
        f8eEnvironment = accountConfig.f8eEnvironment,
        fullAccountId = accountId
      ).onFailure { logError { "Failed to list keysets from F8e: $it" } }
        .mapError { DescriptorBackupError.NetworkError(it) }
        .bind()

      val existingEncryptedDescriptors = listKeysetsResponse.descriptorBackups.orEmpty()
      val wrappedSsek = listKeysetsResponse.wrappedSsek

      if (existingEncryptedDescriptors.isEmpty() && wrappedSsek == null) {
        // This is our first time uploading descriptor backups!
        // TODO(W-11606): Handle there being no keysets returned from f8e
        return@coroutineBinding DescriptorBackupPreparedData.EncryptOnly(
          keysetsToEncrypt = listKeysetsResponse.keysets // contains most recent keyset
        )
      } else if (existingEncryptedDescriptors.isEmpty() || wrappedSsek == null) {
        Err(DecryptionError(cause = IllegalStateException("must have both encrypted descriptors and a ssek"))).bind()
      }

      logDebug {
        "Found ${existingEncryptedDescriptors.size} existing encrypted descriptor backups on F8e"
      }

      // TODO(W-11639): Ensure idempotency - only include the new keyset if it hasn't already been uploaded
      val newKeysets = listOf(newActiveKeyset)

      // If we happen to have the unwrapped SSEK available, perhaps because this is a retry and it
      // was previously populated, we make use of it!
      val unwrappedSsek = wrappedSsek.let {
        ssekDao.get(it).get()
      }

      if (unwrappedSsek != null) {
        DescriptorBackupPreparedData.Available(
          descriptorsToDecrypt = existingEncryptedDescriptors,
          keysetsToEncrypt = newKeysets,
          sealedSsek = wrappedSsek
        )
      } else {
        DescriptorBackupPreparedData.NeedsUnsealed(
          descriptorsToDecrypt = existingEncryptedDescriptors,
          keysetsToEncrypt = newKeysets,
          sealedSsek = wrappedSsek
        )
      }
    }

  /**
   * Upload descriptor backups to F8e after they have been encrypted with hardware.
   * Note: This method expects ALL descriptor backups, not just new ones.
   */
  private suspend fun uploadBackupsToF8e(
    accountId: FullAccountId,
    sealedSsek: SealedSsek,
    descriptorBackups: List<DescriptorBackup>,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hwKeyProof: HwFactorProofOfPossession?,
  ): Result<Unit, DescriptorBackupError> =
    coroutineBinding {
      val accountConfig = getFullAccountConfig()

      logDebug { "Uploading ${descriptorBackups.size} descriptor backups to F8e" }

      updateDescriptorBackupsF8eClient.update(
        f8eEnvironment = accountConfig.f8eEnvironment,
        accountId = accountId,
        descriptorBackups = descriptorBackups,
        sealedSsek = sealedSsek,
        appAuthKey = appAuthKey,
        hwKeyProof = hwKeyProof
      )
        .onFailure { logError { "Failed to update descriptor backups: $it" } }
        .mapError { DescriptorBackupError.NetworkError(it) }
        .bind()
    }

  private suspend fun processDescriptorBackupsForRecovery(
    descriptorsToDecrypt: List<DescriptorBackup>,
    keysetsToEncrypt: List<SpendingKeyset>,
    sealedSsekToEncrypt: SealedSsek,
    sealedSsekToDecrypt: SealedSsek?,
  ): Result<ProcessedDescriptorBackupsResult, DescriptorBackupError> =
    coroutineBinding {
      require(keysetsToEncrypt.isNotEmpty() || descriptorsToDecrypt.isNotEmpty()) {
        "must have keysets to encrypt or decrypt, but had neither"
      }

      // 1. Decrypt existing SSEK-encrypted descriptors
      val decryptedKeysets = sealedSsekToDecrypt?.let {
        unsealDescriptors(
          sealedSsek = sealedSsekToDecrypt,
          encryptedDescriptorBackups = descriptorsToDecrypt
        ).bind()
      } ?: emptyList()

      logDebug { "Decrypted ${decryptedKeysets.size} existing descriptor backups" }

      // 2. Combine all keysets (existing + new)
      val allKeysets = decryptedKeysets + keysetsToEncrypt

      // 3. Encrypt all keysets with new SSEK
      val encryptedNewDescriptors = sealDescriptors(
        sealedSsek = sealedSsekToEncrypt,
        keysets = allKeysets
      ).bind()

      logDebug { "Encrypted ${encryptedNewDescriptors.size} descriptor backups" }

      ProcessedDescriptorBackupsResult(
        encryptedDescriptors = encryptedNewDescriptors,
        allKeysets = allKeysets
      )
    }

  /**
   * Parses a descriptor string to extract the three public keys and create a SpendingKeyset.
   *
   * Expected format: wsh(sortedmulti(2,key1,key2,key3))
   *
   * Key ordering within the descriptor string:
   * - keys[0]: App spending public key
   * - keys[1]: Hardware spending public key
   * - keys[2]: Server (F8e) spending public key
   *
   * This ordering must match the order used when constructing the descriptor
   * in [BitcoinMultiSigDescriptorBuilder.watchingDescriptor]
   */
  private fun parseDescriptorKeys(
    descriptorString: String,
    keysetId: String,
    networkType: BitcoinNetworkType,
  ): Result<SpendingKeyset, DescriptorBackupError> {
    return binding {
      val matchResult = DESCRIPTOR_PATTERN.find(descriptorString)
        ?: Err(DecryptionError(IllegalArgumentException("Invalid descriptor format: $descriptorString"))).bind()

      val keysString = matchResult.groupValues[1]
      val keys = keysString.split(",")
        .map { DescriptorPublicKey(it.trim()) }

      if (keys.size != 3) {
        Err(DecryptionError(IllegalArgumentException("Expected 3 keys in descriptor, got ${keys.size}"))).bind()
      }

      SpendingKeyset(
        localId = uuidGenerator.random(),
        networkType = networkType,
        appKey = AppSpendingPublicKey(keys[0]),
        hardwareKey = HwSpendingPublicKey(keys[1]),
        f8eSpendingKeyset = F8eSpendingKeyset(
          keysetId = keysetId,
          spendingPublicKey = F8eSpendingPublicKey(keys[2])
        )
      )
    }
  }
}

/**
 * Result of processing descriptor backups containing both encrypted descriptors and all keysets.
 */
private data class ProcessedDescriptorBackupsResult(
  val encryptedDescriptors: List<DescriptorBackup>,
  val allKeysets: List<SpendingKeyset>,
)
