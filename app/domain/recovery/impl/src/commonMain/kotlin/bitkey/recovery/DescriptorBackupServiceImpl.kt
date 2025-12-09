package bitkey.recovery

import bitkey.account.*
import bitkey.backup.DescriptorBackup
import bitkey.f8e.account.UpdateDescriptorBackupsF8eClient
import bitkey.recovery.DescriptorBackupError.*
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.account.getAccountOrNull
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.f8e.isPrivateWallet
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
import build.wallet.f8e.recovery.LegacyRemoteKeyset
import build.wallet.f8e.recovery.ListKeysetsF8eClient
import build.wallet.f8e.recovery.toSpendingKeysets
import build.wallet.feature.flags.DescriptorBackupFailsafeFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logDebug
import build.wallet.logging.logFailure
import build.wallet.logging.logInfo
import build.wallet.logging.logWarn
import build.wallet.platform.random.UuidGenerator
import build.wallet.worker.RetryStrategy
import build.wallet.worker.RunStrategy
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Duration.Companion.seconds

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
  private val descriptorBackupFailsafeFeatureFlag: DescriptorBackupFailsafeFeatureFlag,
  private val descriptorBackupVerificationDao: DescriptorBackupVerificationDao,
  bitcoinWalletService: BitcoinWalletService,
) : DescriptorBackupService, DescriptorBackupHealthSyncWorker {
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

  override val retryStrategy = RetryStrategy.Always(delay = 5.seconds, retries = 10)
  override val runStrategy: Set<RunStrategy> = setOf<RunStrategy>(
    RunStrategy.Startup(),
    /** Re-verify descriptor backups whenever the spending wallet changes (e.g. after you recover via cloud backup). */
    RunStrategy.OnEvent(observer = bitcoinWalletService.spendingWallet())
  )

  private val descriptorBackupAad = "Bitkey Descriptor Backup Encryption Version 1.0".encodeUtf8()

  override suspend fun executeWork() {
    ensureActiveKeysetHasDescriptorBackup()
  }

  override suspend fun checkBackupForPrivateKeyset(keysetId: String): Result<Unit, Throwable> {
    return coroutineBinding {
      // Check feature flag
      if (!descriptorBackupFailsafeFeatureFlag.isEnabled()) {
        return@coroutineBinding
      }

      // Check cache for the specific keyset
      val status = descriptorBackupVerificationDao
        .getVerifiedBackup(keysetId)
        .bind()

      if (status == null) {
        Err(IllegalStateException("No descriptor backup exists for private keyset $keysetId.")).bind()
      }
    }
  }

  override suspend fun prepareDescriptorBackupsForRecovery(
    accountId: FullAccountId,
    factorToRecover: PhysicalFactor,
    f8eSpendingKeyset: F8eSpendingKeyset,
    appSpendingKey: AppSpendingPublicKey,
    hwSpendingKey: HwSpendingPublicKey,
  ): Result<DescriptorBackupPreparedData, Error> {
    return coroutineBinding {
      val accountConfig = getAccountConfig()
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
        )
          .logFailure { "Failed to prepare descriptor backups for hardware recovery" }
          .bind()

        PhysicalFactor.App -> prepareForAppRecovery(
          accountId = accountId,
          accountConfig = accountConfig,
          newActiveKeyset = newActiveKeyset
        )
          .logFailure { "Failed to prepare descriptor backups for app recovery" }
          .bind()
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

      logInfo { "Uploading initial descriptor backups for account $accountId" }

      // Seal the descriptors using the provided SSEK
      val encryptedDescriptors = sealDescriptors(
        sealedSsek = sealedSsekForEncryption,
        keysets = keysetsToEncrypt
      )
        .logFailure { "Failed to seal descriptor backup during onboarding" }
        .bind()

      // Upload the encrypted descriptors to F8e
      uploadBackupsToF8e(
        accountId = accountId,
        sealedSsek = sealedSsekForEncryption,
        descriptorBackups = encryptedDescriptors,
        appAuthKey = appAuthKey,
        hwKeyProof = null
      ).bind()

      verifyDescriptorBackups(
        accountId = accountId,
        originalContent = keysetsToEncrypt,
        sealedSsek = sealedSsekForEncryption
      )
        .logFailure { "Descriptor backup verification failed" }
        .bind()

      // Replace cache with newly verified backups
      descriptorBackupVerificationDao.replaceAllVerifiedBackups(
        keysetsToEncrypt.map { keyset ->
          VerifiedBackup(keysetId = keyset.f8eSpendingKeyset.keysetId)
        }
      )
        .mapError { VerificationFailed("Failed to update cache: ${it.message}") }
        .bind()
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
      )
        .logFailure { "Failed to process descriptor backups for recovery" }
        .bind()

      uploadBackupsToF8e(
        accountId = accountId,
        descriptorBackups = processedResult.encryptedDescriptors,
        sealedSsek = sealedSsekForEncryption,
        appAuthKey = appAuthKey,
        hwKeyProof = hwKeyProof
      ).bind()

      verifyDescriptorBackups(
        accountId = accountId,
        originalContent = processedResult.allKeysets,
        sealedSsek = sealedSsekForEncryption
      )
        .logFailure { "Descriptor backup verification failed" }
        .bind()

      // Update cache with all keysets that were just uploaded
      descriptorBackupVerificationDao.replaceAllVerifiedBackups(
        processedResult.allKeysets.map { keyset ->
          VerifiedBackup(keysetId = keyset.f8eSpendingKeyset.keysetId)
        }
      )
        .mapError { VerificationFailed("Failed to update cache: ${it.message}") }
        .bind()

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

        val encryptedPrivateWalletRootXpub = keyset.f8eSpendingKeyset.privateWalletRootXpub?.let {
          symmetricKeyEncryptor.seal(
            unsealedData = it.encodeUtf8(),
            key = ssek.key,
            aad = descriptorBackupAad
          )
        }

        DescriptorBackup(
          keysetId = keyset.f8eSpendingKeyset.keysetId,
          sealedDescriptor = encryptedDescriptor,
          privateWalletRootXpub = encryptedPrivateWalletRootXpub
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
      val accountConfig = getAccountConfig()

      encryptedDescriptorBackups.map { encryptedBackup ->
        // Decrypt the descriptor
        val decryptedDescriptorData = symmetricKeyEncryptor.unseal(
          ciphertext = encryptedBackup.sealedDescriptor,
          key = ssek.key,
          aad = descriptorBackupAad
        ).utf8()

        // Decrypt the private wallet root xpub
        val decryptedPrivateWalletRootXpub = encryptedBackup.privateWalletRootXpub?.let {
          symmetricKeyEncryptor.unseal(
            ciphertext = it,
            key = ssek.key,
            aad = descriptorBackupAad
          ).utf8()
        }

        // Parse the descriptor and convert to SpendingKeyset
        parseDescriptorKeys(
          descriptorString = decryptedDescriptorData,
          privateWalletRootXpub = decryptedPrivateWalletRootXpub,
          keysetId = encryptedBackup.keysetId,
          networkType = accountConfig.bitcoinNetworkType
        ).bind()
      }
    }
  }

  /**
   * Verifies that the most recent f8e backup worked as expected:
   * 1. The number of descriptor backups matches.
   * 2. The sealed SSEK on the server matches the local one used for encryption.
   * 3. The contents of all encrypted backups match the originals.
   *
   * We're highly paranoid about this, because if the backups are not correct, we lose funds.
   */
  private suspend fun verifyDescriptorBackups(
    accountId: FullAccountId,
    originalContent: List<SpendingKeyset>,
    sealedSsek: SealedSsek,
  ): Result<Unit, DescriptorBackupError> {
    return coroutineBinding {
      logDebug { "Verifying uploaded descriptor backups for account $accountId" }

      // Download and verify the uploaded backups
      val accountConfig = getAccountConfig()
      val listKeysetsResponse = listKeysetsF8eClient.listKeysets(
        f8eEnvironment = accountConfig.f8eEnvironment,
        fullAccountId = accountId
      )
        .logFailure { "Failed to download descriptor backups for verification" }
        .mapError { NetworkError(it) }
        .bind()

      val downloadedBackups = listKeysetsResponse.descriptorBackups
      val downloadedSsek = listKeysetsResponse.wrappedSsek
        ?: Err(VerificationFailed("No wrapped ssek found after upload")).bind()

      // Step 1: Check that we have the same number of backups
      if (originalContent.size != downloadedBackups.size) {
        Err(VerificationFailed("Descriptor backup count mismatch: expected ${originalContent.size}, got ${downloadedBackups.size}")).bind()
      }

      // Step 2: Ensure the sealed SSEK returned by F8e matches the one we used.
      if (downloadedSsek != sealedSsek) {
        Err(VerificationFailed("Mismatch between provided and server-sealed SSEK during backup verification.")).bind()
      }

      // Step 3: Content verification for each backup
      val downloadedByKeysetId = downloadedBackups.associateBy { it.keysetId }

      // For each original keyset, unseal downloaded backup and verify keys
      originalContent.forEach { originalKeyset ->
        val keysetId = originalKeyset.f8eSpendingKeyset.keysetId
        val downloadedBackup = downloadedByKeysetId[keysetId]
          ?: Err(VerificationFailed("Missing backup for keyset: $keysetId")).bind()

        val ssek = ssekDao.get(sealedSsek).get() ?: Err(SsekNotFound).bind()
        val decryptedDescriptorData = symmetricKeyEncryptor.unseal(
          ciphertext = downloadedBackup.sealedDescriptor,
          key = ssek.key,
          aad = descriptorBackupAad
        ).utf8()

        val decryptedPrivateWalletRootXpub = downloadedBackup.privateWalletRootXpub?.let {
          symmetricKeyEncryptor.unseal(
            ciphertext = it,
            key = ssek.key,
            aad = descriptorBackupAad
          ).utf8()
        }

        val parsedKeyset = parseDescriptorKeys(
          descriptorString = decryptedDescriptorData,
          keysetId = downloadedBackup.keysetId,
          networkType = accountConfig.bitcoinNetworkType,
          privateWalletRootXpub = decryptedPrivateWalletRootXpub
        ).bind()

        // Ignore the local id for comparison purposes as it is randomly created every time we
        // call ListKeysets.
        if (parsedKeyset.appKey != originalKeyset.appKey ||
          parsedKeyset.hardwareKey != originalKeyset.hardwareKey ||
          parsedKeyset.f8eSpendingKeyset != originalKeyset.f8eSpendingKeyset
        ) {
          Err(VerificationFailed("Descriptor keys mismatch for keyset $keysetId.")).bind()
        }
      }

      logInfo { "Successfully verified descriptor backups" }
    }
  }

  private fun getAccountConfig(): AccountConfig =
    when (val config = accountConfigService.activeOrDefaultConfig().value) {
      is DefaultAccountConfig -> config.toFullAccountConfig()
      is FullAccountConfig -> config
      is LiteAccountConfig -> config
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
    accountConfig: AccountConfig,
    newActiveKeyset: SpendingKeyset,
  ): Result<DescriptorBackupPreparedData, DescriptorBackupError> =
    coroutineBinding {
      logInfo { "Preparing descriptor backups for Lost Hardware recovery" }

      val keybox = accountService.getAccount<FullAccount>()
        .mapError { AccountNotFound }
        .map { it.keybox }
        .bind()

      // This is the private wallet case, guaranteed to have all keysets (private or we have them all)
      if (keybox.canUseKeyboxKeysets) {
        // If the local keysets are authoritative, we can use them directly
        logInfo { "Using local keysets for Lost Hardware recovery; encrypting all keysets" }
        DescriptorBackupPreparedData.EncryptOnly(
          keysetsToEncrypt = keybox.keysets + newActiveKeyset
        )
      } else {
        // Otherwise, we must retrieve the latest keysets from f8e
        logInfo { "Retrieving f8e keysets for Lost Hardware recovery" }

        val f8eKeysetsResponse = listKeysetsF8eClient.listKeysets(
          f8eEnvironment = accountConfig.f8eEnvironment,
          fullAccountId = accountId
        ).logFailure { "Failed to list keysets from F8e" }
          .mapError { NetworkError(it) }
          .bind()
          .keysets

        // Log any private keysets being filtered out (except the newActiveKeyset)
        f8eKeysetsResponse.forEach { keyset ->
          if (keyset !is LegacyRemoteKeyset) {
            if (keyset.keysetId != newActiveKeyset.f8eSpendingKeyset.keysetId) {
              logWarn { "Filtering out private keyset during Lost Hardware recovery: ${keyset.keysetId}" }
            }
          }
        }

        val f8eKeysets = f8eKeysetsResponse
          .filter {
            // Filter out any private keysets; if there were any valid ones beyond the newly created
            // keyset, canUseKeyboxKeysets would be set to true
            it is LegacyRemoteKeyset
          }
          .toSpendingKeysets(uuidGenerator)

        val keysetsToEncrypt = if (f8eKeysets.map { it.f8eSpendingKeyset.keysetId }
            .contains(newActiveKeyset.f8eSpendingKeyset.keysetId)
        ) {
          logInfo { "New keyset already exists in f8e keysets, skipping" }
          f8eKeysets
        } else {
          f8eKeysets + newActiveKeyset
        }

        DescriptorBackupPreparedData.EncryptOnly(
          keysetsToEncrypt = keysetsToEncrypt
        )
      }
    }

  private suspend fun prepareForAppRecovery(
    accountId: FullAccountId,
    accountConfig: AccountConfig,
    newActiveKeyset: SpendingKeyset,
  ): Result<DescriptorBackupPreparedData, DescriptorBackupError> =
    coroutineBinding {
      logInfo { "Preparing descriptor backups for Lost App and Cloud recovery" }

      val listKeysetsResponse = listKeysetsF8eClient.listKeysets(
        f8eEnvironment = accountConfig.f8eEnvironment,
        fullAccountId = accountId
      ).logFailure { "Failed to list keysets from F8e" }
        .mapError { NetworkError(it) }
        .bind()

      val existingEncryptedDescriptors = listKeysetsResponse.descriptorBackups
      val wrappedSsek = listKeysetsResponse.wrappedSsek
      val f8eKeysets = listKeysetsResponse.keysets

      if (existingEncryptedDescriptors.isEmpty() && wrappedSsek == null) {
        // This is our first time uploading descriptor backups!
        logInfo { "No existing encrypted descriptor backups found on F8e, encrypting all f8e keysets." }

        // Log any private keysets being filtered out (except the newActiveKeyset)
        f8eKeysets.forEach { keyset ->
          if (keyset !is LegacyRemoteKeyset) {
            if (keyset.keysetId != newActiveKeyset.f8eSpendingKeyset.keysetId) {
              logInfo { "Filtering out private keyset during Lost App recovery: ${keyset.keysetId}" }
            }
          }
        }

        // F8e will return the most recently created keyset, which could be private or legacy. We
        // need to filter it out. We can eagerly filter all private keysets since if this recovery
        // wasn't the first one to a private keyset, we would have had descriptor backups.
        val f8eLegacyKeysets =
          f8eKeysets.filterIsInstance<LegacyRemoteKeyset>().toSpendingKeysets(uuidGenerator)

        val keysetsToEncrypt = if (f8eLegacyKeysets.map { it.f8eSpendingKeyset.keysetId }
            .contains(newActiveKeyset.f8eSpendingKeyset.keysetId)
        ) {
          logInfo { "New keyset already exists in f8e keysets, skipping" }
          f8eLegacyKeysets
        } else {
          f8eLegacyKeysets + newActiveKeyset
        }

        return@coroutineBinding DescriptorBackupPreparedData.EncryptOnly(
          keysetsToEncrypt = keysetsToEncrypt
        )
      } else if (existingEncryptedDescriptors.isEmpty() || wrappedSsek == null) {
        Err(DecryptionError(cause = IllegalStateException("must have both encrypted descriptors and a ssek"))).bind()
      }

      logInfo {
        "Found ${existingEncryptedDescriptors.size} existing encrypted descriptor backups on F8e"
      }

      // Check if the new keyset has already been backed up; this could happen on a retry, such as if
      // we get a network failure but the server did actually receive the backup.
      val existingBackups = existingEncryptedDescriptors.map { it.keysetId }.toSet()
      val newKeysets = if (existingBackups.contains(newActiveKeyset.f8eSpendingKeyset.keysetId)) {
        logInfo {
          "New keyset ${newActiveKeyset.f8eSpendingKeyset.keysetId} already exists in backups, skipping"
        }
        emptyList()
      } else {
        listOf(newActiveKeyset)
      }

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
      val accountConfig = getAccountConfig()

      logInfo { "Uploading ${descriptorBackups.size} descriptor backups to F8e" }

      updateDescriptorBackupsF8eClient.update(
        f8eEnvironment = accountConfig.f8eEnvironment,
        accountId = accountId,
        descriptorBackups = descriptorBackups,
        sealedSsek = sealedSsek,
        appAuthKey = appAuthKey,
        hwKeyProof = hwKeyProof
      )
        .logFailure { "Failed to update descriptor backups" }
        .mapError { NetworkError(it) }
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

      logInfo { "Decrypted ${decryptedKeysets.size} existing descriptor backups" }

      // 2. Combine all keysets (existing + new)
      val allKeysets = decryptedKeysets + keysetsToEncrypt

      // 3. Encrypt all keysets with new SSEK
      val encryptedNewDescriptors = sealDescriptors(
        sealedSsek = sealedSsekToEncrypt,
        keysets = allKeysets
      ).bind()

      logInfo { "Encrypted ${encryptedNewDescriptors.size} descriptor backups" }

      ProcessedDescriptorBackupsResult(
        encryptedDescriptors = encryptedNewDescriptors,
        allKeysets = allKeysets
      )
    }

  override suspend fun parseDescriptorKeys(
    descriptorString: String,
    privateWalletRootXpub: String?,
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
          spendingPublicKey = F8eSpendingPublicKey(keys[2]),
          privateWalletRootXpub = privateWalletRootXpub
        )
      )
    }
  }

  /**
   * Ensures that the [build.wallet.bitkey.keybox.Keybox.activeSpendingKeyset] has an associated
   * descriptor backup.
   *
   * It will try to do so via a cached copy first via [DescriptorBackupVerificationDao]. If not
   * found in cache, it will make a call to f8e to get the most recent descriptor backups.
   */
  private suspend fun ensureActiveKeysetHasDescriptorBackup(): Result<Unit, Error> {
    return coroutineBinding {
      logInfo { "Ensuring descriptor backup exists for active keyset" }

      // Get current account
      val account = accountService.getAccountOrNull<FullAccount>()
        .bind()
      if (account == null) return@coroutineBinding

      // Skip if active keyset is not private
      if (!account.keybox.activeSpendingKeyset.f8eSpendingKeyset.isPrivateWallet) {
        logInfo { "Active keyset is not private, skipping descriptor backup verification" }
        return@coroutineBinding
      }

      val activeKeysetId = account.keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId

      // Check cache first
      val cachedVerification = descriptorBackupVerificationDao
        .getVerifiedBackup(activeKeysetId)
        .bind()

      if (cachedVerification != null) {
        logInfo { "Found cached verification for active keyset $activeKeysetId" }
        return@coroutineBinding
      }

      // Cache miss - query F8e for the latest descriptor backups
      listKeysetsF8eClient.listKeysets(
        f8eEnvironment = account.config.f8eEnvironment,
        fullAccountId = account.accountId
      )
        .logFailure { "Failed to list keysets from F8e during descriptor backup verification" }
        .fold(
          success = { listKeysetsResponse ->
            val descriptorBackups = listKeysetsResponse.descriptorBackups

            // Update cache with all keysets that have backups on F8e
            descriptorBackupVerificationDao.replaceAllVerifiedBackups(
              descriptorBackups.map { backup ->
                VerifiedBackup(keysetId = backup.keysetId)
              }
            )
              .logFailure { "Failed to update cache with verified backups" }
              .bind()
          },
          failure = {
            logInfo { "Network failure during F8e query, no cached verification available" }
          }
        )
    }.logFailure { "Failed to verify descriptor backup for active keyset" }
  }
}

/**
 * Result of processing descriptor backups containing both encrypted descriptors and all keysets.
 */
private data class ProcessedDescriptorBackupsResult(
  val encryptedDescriptors: List<DescriptorBackup>,
  val allKeysets: List<SpendingKeyset>,
)
