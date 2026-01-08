package build.wallet.cloud.backup.v2

import bitkey.account.FullAccountConfig
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.catchingResult
import build.wallet.cloud.backup.CloudBackup
import build.wallet.cloud.backup.CloudBackupRestorer
import build.wallet.cloud.backup.CloudBackupRestorer.CloudBackupRestorerError
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.cloud.backup.JsonSerializer
import build.wallet.cloud.backup.SocRecV1BackupFeatures
import build.wallet.cloud.backup.bitcoinNetworkType
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.cloud.backup.f8eEnvironment
import build.wallet.cloud.backup.isTestAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SymmetricKeyEncryptor
import build.wallet.platform.random.UuidGenerator
import build.wallet.relationships.RelationshipsKeysDao
import build.wallet.relationships.saveKey
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding

/**
 * Shared implementation for restoring cloud backups across all versions.
 * Supports V2, V3, and future cloud backup versions.
 */
@BitkeyInject(AppScope::class)
class CloudBackupRestorerImpl(
  private val csekDao: CsekDao,
  private val symmetricKeyEncryptor: SymmetricKeyEncryptor,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val uuidGenerator: UuidGenerator,
  private val relationshipsKeysDao: RelationshipsKeysDao,
  private val jsonSerializer: JsonSerializer,
) : CloudBackupRestorer {
  override suspend fun restore(
    cloudBackup: CloudBackup,
  ): Result<AccountRestoration, CloudBackupRestorerError> {
    require(cloudBackup is SocRecV1BackupFeatures) {
      "CloudBackup must implement SocRecV1BackupFeatures"
    }
    return coroutineBinding {
      val keysInfo = decryptCloudBackup(cloudBackup).bind()
      restoreWithDecryptedKeys(cloudBackup, keysInfo).bind()
    }
  }

  override suspend fun restoreWithDecryptedKeys(
    cloudBackup: CloudBackup,
    keysInfo: FullAccountKeys,
  ): Result<AccountRestoration, CloudBackupRestorerError> {
    require(cloudBackup is SocRecV1BackupFeatures) {
      "CloudBackup must implement SocRecV1BackupFeatures"
    }
    return restoreWithDecryptedKeysInternal(cloudBackup, keysInfo)
  }

  override suspend fun decryptCloudBackup(
    cloudBackup: CloudBackup,
  ): Result<FullAccountKeys, CloudBackupRestorerError> {
    require(cloudBackup is SocRecV1BackupFeatures) {
      "CloudBackup must implement SocRecV1BackupFeatures"
    }
    return decryptCloudBackupInternal(cloudBackup)
  }

  private suspend fun <T> restoreWithDecryptedKeysInternal(
    cloudBackup: T,
    keysInfo: FullAccountKeys,
  ): Result<AccountRestoration, CloudBackupRestorerError>
      where T : CloudBackup, T : SocRecV1BackupFeatures =
    coroutineBinding {
      val fullAccountFields = requireNotNull(cloudBackup.fullAccountFields)

      // Store auth private keys
      appPrivateKeyDao.storeAppKeyPair(cloudBackup.appRecoveryAuthKeypair).mapError {
        CloudBackupRestorerError.AppAuthKeypairStorageError(it)
      }.bind()
      appPrivateKeyDao.storeAppKeyPair(keysInfo.appGlobalAuthKeypair).mapError {
        CloudBackupRestorerError.AppAuthKeypairStorageError(it)
      }.bind()

      // Store spending private keys
      keysInfo.appSpendingKeys.forEach { (publicKey, privateKey) ->
        appPrivateKeyDao.storeAppSpendingKeyPair(
          AppSpendingKeypair(
            publicKey = publicKey,
            privateKey = privateKey
          )
        )
          .mapError {
            CloudBackupRestorerError.AppSpendingKeypairStorageError(it)
          }.bind()
      }

      // Store trusted contact identity key
      relationshipsKeysDao.saveKey(cloudBackup.delegatedDecryptionKeypair)
        .mapError(CloudBackupRestorerError::SocRecTrustedContactIdentityKeyStorageError)
        .bind()

      AccountRestoration(
        activeSpendingKeyset = keysInfo.activeSpendingKeyset,
        keysets = keysInfo.keysets,
        activeAppKeyBundle =
          AppKeyBundle(
            localId = uuidGenerator.random(),
            spendingKey = keysInfo.activeSpendingKeyset.appKey,
            authKey = keysInfo.appGlobalAuthKeypair.publicKey,
            networkType = cloudBackup.bitcoinNetworkType,
            recoveryAuthKey = cloudBackup.appRecoveryAuthKeypair.publicKey
          ),
        activeHwKeyBundle = HwKeyBundle(
          localId = uuidGenerator.random(),
          spendingKey = keysInfo.activeHwSpendingKey,
          authKey = keysInfo.activeHwAuthKey,
          networkType = cloudBackup.bitcoinNetworkType
        ),
        config =
          FullAccountConfig(
            bitcoinNetworkType = cloudBackup.bitcoinNetworkType,
            f8eEnvironment = cloudBackup.f8eEnvironment,
            isHardwareFake = fullAccountFields.isFakeHardware,
            isTestAccount = cloudBackup.isTestAccount,
            isUsingSocRecFakes = cloudBackup.isUsingSocRecFakes,
            hardwareType = fullAccountFields.hardwareType
          ),
        cloudBackupForLocalStorage = cloudBackup,
        appGlobalAuthKeyHwSignature = fullAccountFields.appGlobalAuthKeyHwSignature
      )
    }

  private suspend fun <T> decryptCloudBackupInternal(
    cloudBackup: T,
  ): Result<FullAccountKeys, CloudBackupRestorerError>
      where T : CloudBackup, T : SocRecV1BackupFeatures {
    val fullAccountFields = requireNotNull(cloudBackup.fullAccountFields)

    val pkek =
      csekDao.get(fullAccountFields.sealedHwEncryptionKey).get()
        ?: return Err(CloudBackupRestorerError.PkekMissingError)

    val keysInfoEncoded =
      catchingResult {
        symmetricKeyEncryptor.unsealNoMetadata(
          sealedData = fullAccountFields.hwFullAccountKeysCiphertext,
          key = pkek.key
        )
      }.getOrElse { return Err(CloudBackupRestorerError.AccountBackupDecryptionError(cause = it)) }
        .utf8()

    return jsonSerializer.decodeFromStringResult<FullAccountKeys>(keysInfoEncoded)
      .mapError {
        CloudBackupRestorerError.AccountBackupDecodingError(it)
      }
  }
}
