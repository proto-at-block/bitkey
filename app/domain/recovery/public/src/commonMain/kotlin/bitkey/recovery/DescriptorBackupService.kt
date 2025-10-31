package bitkey.recovery

import bitkey.backup.DescriptorBackup
import bitkey.recovery.DescriptorBackupError.SsekNotFound
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.descriptor.BitcoinMultiSigDescriptorBuilder
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.crypto.PublicKey
import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result

/**
 * Service responsible for encryption, decryption, and upload of descriptor backups during account recovery processes.
 *
 * This service handles:
 * 1. Preparing descriptor backups for recovery based on the factor being recovered (App or Hardware)
 * 2. Encrypting descriptors with Server Storage Encryption Keys (SSEK)
 * 3. Decrypting existing descriptors during recovery
 * 4. Uploading encrypted descriptors to F8e
 * 5. Verifying descriptor backup health for the active keyset
 */
interface DescriptorBackupService {
  /**
   * Verifies that a descriptor backup exists for a private keyset before allowing operations
   * that generate addresses (e.g., address generation, sweep destination).
   *
   * This method checks the [DescriptorBackupVerificationDao] cache to determine if a descriptor
   * backup has been verified to exist for the given keyset. The cache is kept up-to-date by the background worker
   * [DescriptorBackupHealthSyncWorker] which runs on app launch and when the spending wallet changes.
   *
   * This is a failsafe to prevent generating addresses for private keysets without a backup,
   * which could lead to funds loss if the backup is lost.
   *
   * Behavior:
   * - Returns [Ok] if feature flag is disabled
   * - Returns [Ok] if backup exists for the keyset
   * - Returns [Err] if no backup exists
   *
   * @param keysetId The server keysetId of the keyset to verify
   * @return [Result] containing [Unit] if backup exists or check passes,
   *         or [Error] if backup is missing for a private keyset
   */
  suspend fun checkBackupForPrivateKeyset(keysetId: String): Result<Unit, Throwable>

  /**
   * Prepares descriptor backup data for the recovery process based on the factor being recovered.
   *
   * This method determines:
   * - For Hardware recovery: Retrieves keysets from local storage or F8e if not found locally
   * - For App recovery: Fetches existing encrypted descriptors and SSEK from F8e
   *
   * The returned [DescriptorBackupPreparedData] indicates:
   * - [DescriptorBackupPreparedData.EncryptOnly]: Only new descriptors need encryption (first time or hardware recovery)
   * - [DescriptorBackupPreparedData.NeedsUnsealed]: Existing SSEK needs hardware unsealing
   * - [DescriptorBackupPreparedData.Available]: SSEK is available and ready for decryption/encryption
   *
   * @param accountId The full account identifier for the account being recovered
   * @param factorToRecover The physical factor (hardware or app) that is being recovered
   * @param f8eSpendingKeyset The F8e spending keyset associated with the account
   * @param appSpendingKey The app's spending public key
   * @param hwSpendingKey The hardware wallet's spending public key
   *
   * @return [Result] containing [DescriptorBackupPreparedData] with organized backup information,
   *         or [Error] if preparation fails (e.g. network error, account not found)
   */
  suspend fun prepareDescriptorBackupsForRecovery(
    accountId: FullAccountId,
    factorToRecover: PhysicalFactor,
    f8eSpendingKeyset: F8eSpendingKeyset,
    appSpendingKey: AppSpendingPublicKey,
    hwSpendingKey: HwSpendingPublicKey,
  ): Result<DescriptorBackupPreparedData, Error>

  /**
   * Uploads encrypted descriptor backups to F8e on onboarding.
   *
   * This method is different from [uploadDescriptorBackups] because it does not require a hardware
   * key proof when uploading the backups before onboarding is complete.
   *
   * @param accountId The full account identifier
   * @param sealedSsekForEncryption The sealed SSEK for encrypting the descriptors
   * @param appAuthKey The app's authentication key for F8e
   * @param keysetsToEncrypt The list of spending keysets to encrypt as descriptors
   */
  suspend fun uploadOnboardingDescriptorBackup(
    accountId: FullAccountId,
    sealedSsekForEncryption: SealedSsek,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    keysetsToEncrypt: List<SpendingKeyset>,
  ): Result<Unit, DescriptorBackupError>

  /**
   * Uploads encrypted descriptor backups to F8e after processing them for recovery.
   *
   * This method:
   * 1. Decrypts existing descriptors using [sealedSsekForDecryption] if provided
   * 2. Encrypts new keysets using [sealedSsekForEncryption]
   * 3. Combines and uploads all descriptors to F8e
   *
   * @param accountId The full account identifier
   * @param sealedSsekForDecryption The sealed SSEK for decrypting existing descriptors (null if no existing descriptors)
   * @param sealedSsekForEncryption The sealed SSEK for encrypting new descriptors
   * @param appAuthKey The app's authentication key for F8e
   * @param hwKeyProof Hardware proof of possession for the upload operation
   * @param descriptorsToDecrypt List of existing encrypted descriptor backups to decrypt
   * @param keysetsToEncrypt List of spending keysets to encrypt as descriptors
   *
   * @return [Result] containing all keysets (both decrypted and newly encrypted),
   *         or [DescriptorBackupError] if any operation fails:
   *         - [DescriptorBackupError.SsekNotFound] if SSEK is not found in storage
   *         - [DescriptorBackupError.DecryptionError] if descriptor decryption fails
   *         - [DescriptorBackupError.NetworkError] if F8e communication fails
   */
  suspend fun uploadDescriptorBackups(
    accountId: FullAccountId,
    sealedSsekForDecryption: SealedSsek?,
    sealedSsekForEncryption: SealedSsek,
    appAuthKey: PublicKey<AppGlobalAuthKey>,
    hwKeyProof: HwFactorProofOfPossession,
    descriptorsToDecrypt: List<DescriptorBackup>,
    keysetsToEncrypt: List<SpendingKeyset>,
  ): Result<List<SpendingKeyset>, DescriptorBackupError>

  /**
   * Encrypts spending keysets into descriptor backups using a Server Storage Encryption Key (SSEK).
   *
   * For each keyset, this method:
   * 1. Builds a watching descriptor from the keyset's public keys
   * 2. Encrypts the descriptor using the SSEK
   * 3. Creates a [DescriptorBackup] with the encrypted data
   *
   * @param sealedSsek The sealed SSEK to use for encryption
   * @param keysets The list of spending keysets to encrypt
   *
   * @return [Result] containing the list of encrypted [DescriptorBackup]s,
   *         or [SsekNotFound] if the SSEK is not found in storage
   */
  suspend fun sealDescriptors(
    sealedSsek: SealedSsek,
    keysets: List<SpendingKeyset>,
  ): Result<List<DescriptorBackup>, SsekNotFound>

  /**
   * Decrypts encrypted descriptor backups into spending keysets using a Server Storage Encryption Key (SSEK).
   *
   * For each backup, this method:
   * 1. Decrypts the descriptor using the SSEK
   * 2. Parses the decrypted descriptor string
   * 3. Creates a [SpendingKeyset] from the extracted public keys
   *
   * @param sealedSsek The sealed SSEK to use for decryption
   * @param encryptedDescriptorBackups The list of encrypted descriptor backups
   *
   * @return [Result] containing the list of decrypted [SpendingKeyset]s,
   *         or [DescriptorBackupError] if:
   *         - [SsekNotFound] if the SSEK is not found in storage
   *         - [DecryptionError] if descriptor decryption or parsing fails
   */
  suspend fun unsealDescriptors(
    sealedSsek: SealedSsek,
    encryptedDescriptorBackups: List<DescriptorBackup>,
  ): Result<List<SpendingKeyset>, DescriptorBackupError>

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
  suspend fun parseDescriptorKeys(
    descriptorString: String,
    privateWalletRootXpub: String?,
    keysetId: String,
    networkType: BitcoinNetworkType,
  ): Result<SpendingKeyset, DescriptorBackupError>
}

sealed class DescriptorBackupError : Error() {
  /** The [build.wallet.cloud.backup.csek.Ssek] for the provided [SealedSsek] was not found in [build.wallet.cloud.backup.csek.SsekDao]. */
  data object SsekNotFound : DescriptorBackupError()

  /** Failed to decrypt or parse descriptor backups. */
  data class DecryptionError(override val cause: Throwable?) : DescriptorBackupError()

  /** The account was not found in local storage. */
  data object AccountNotFound : DescriptorBackupError()

  /** Communication with F8e failed. */
  data class NetworkError(override val cause: Throwable) : DescriptorBackupError()

  /** Verification of the decrypted descriptor backups failed. */
  data class VerificationFailed(override val message: String) : DescriptorBackupError()
}

/**
 * Data prepared for NFC transaction to process descriptor backups.
 *
 * This sealed interface represents different states of descriptor backup preparation:
 */
sealed interface DescriptorBackupPreparedData {
  /**
   * Only new descriptors need to be encrypted. This occurs in two scenarios:
   * 1. First time uploading descriptor backups
   * 2. Hardware recovery where we can't decrypt existing backups
   */
  data class EncryptOnly(
    val keysetsToEncrypt: List<SpendingKeyset>,
  ) : DescriptorBackupPreparedData

  /**
   * We have existing encrypted descriptors and an SSEK that needs hardware unsealing.
   * This occurs during app recovery when the SSEK is not yet available in storage.
   */
  data class NeedsUnsealed(
    val descriptorsToDecrypt: List<DescriptorBackup>,
    val keysetsToEncrypt: List<SpendingKeyset>,
    val sealedSsek: SealedCsek,
  ) : DescriptorBackupPreparedData

  /**
   * We have existing encrypted descriptors and the SSEK is ready in storage.
   * This typically occurs during a retry of app recovery where the SSEK was previously unsealed.
   */
  data class Available(
    val descriptorsToDecrypt: List<DescriptorBackup>,
    val keysetsToEncrypt: List<SpendingKeyset>,
    val sealedSsek: SealedCsek,
  ) : DescriptorBackupPreparedData
}

/**
 * Represents the health status of descriptor backups for a given spending keyset.
 */
sealed interface DescriptorBackupStatus {
  /**
   * Descriptor backup exists and is valid for the keyset.
   */
  data object BackupExists : DescriptorBackupStatus

  /**
   * No backup found for the keyset.
   */
  data object Missing : DescriptorBackupStatus
}
