package build.wallet.cloud.backup.v2

import bitkey.account.FullAccountConfig
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.catchingResult
import build.wallet.cloud.backup.CloudBackupV3
import build.wallet.cloud.backup.CloudBackupV3Restorer
import build.wallet.cloud.backup.CloudBackupV3Restorer.CloudBackupV3RestorerError
import build.wallet.cloud.backup.CloudBackupV3Restorer.CloudBackupV3RestorerError.*
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.cloud.backup.JsonSerializer
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SymmetricKeyEncryptor
import build.wallet.platform.random.UuidGenerator
import build.wallet.relationships.RelationshipsKeysDao
import build.wallet.relationships.saveKey
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding

@BitkeyInject(AppScope::class)
class CloudBackupV3RestorerImpl(
  private val csekDao: CsekDao,
  private val symmetricKeyEncryptor: SymmetricKeyEncryptor,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val uuidGenerator: UuidGenerator,
  private val relationshipsKeysDao: RelationshipsKeysDao,
  private val jsonSerializer: JsonSerializer,
) : CloudBackupV3Restorer {
  override suspend fun restore(
    cloudBackupV3: CloudBackupV3,
  ): Result<AccountRestoration, CloudBackupV3RestorerError> {
    return coroutineBinding {
      val keysInfo = decryptCloudBackup(cloudBackupV3).bind()

      restoreWithDecryptedKeys(cloudBackupV3, keysInfo).bind()
    }
  }

  override suspend fun restoreWithDecryptedKeys(
    cloudBackupV3: CloudBackupV3,
    keysInfo: FullAccountKeys,
  ) = coroutineBinding {
    val fullAccountFields = requireNotNull(cloudBackupV3.fullAccountFields)

    // Store auth private keys
    appPrivateKeyDao.storeAppKeyPair(cloudBackupV3.appRecoveryAuthKeypair).mapError {
      AppAuthKeypairStorageError(it)
    }.bind()
    appPrivateKeyDao.storeAppKeyPair(keysInfo.appGlobalAuthKeypair).mapError {
      AppAuthKeypairStorageError(it)
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
          AppSpendingKeypairStorageError(it)
        }.bind()
    }

    // Store trusted contact identity key
    relationshipsKeysDao.saveKey(cloudBackupV3.delegatedDecryptionKeypair)
      .mapError(::SocRecTrustedContactIdentityKeyStorageError)
      .bind()

    AccountRestoration(
      activeSpendingKeyset = keysInfo.activeSpendingKeyset,
      keysets = keysInfo.keysets,
      activeAppKeyBundle =
        AppKeyBundle(
          localId = uuidGenerator.random(),
          spendingKey = keysInfo.activeSpendingKeyset.appKey,
          authKey = keysInfo.appGlobalAuthKeypair.publicKey,
          networkType = cloudBackupV3.bitcoinNetworkType,
          recoveryAuthKey = cloudBackupV3.appRecoveryAuthKeypair.publicKey
        ),
      activeHwKeyBundle = HwKeyBundle(
        localId = uuidGenerator.random(),
        spendingKey = keysInfo.activeHwSpendingKey,
        authKey = keysInfo.activeHwAuthKey,
        networkType = cloudBackupV3.bitcoinNetworkType
      ),
      config =
        FullAccountConfig(
          bitcoinNetworkType = cloudBackupV3.bitcoinNetworkType,
          f8eEnvironment = cloudBackupV3.f8eEnvironment,
          isHardwareFake = fullAccountFields.isFakeHardware,
          isTestAccount = cloudBackupV3.isTestAccount,
          isUsingSocRecFakes = cloudBackupV3.isUsingSocRecFakes,
          hardwareType = fullAccountFields.hardwareType
        ),
      cloudBackupForLocalStorage = cloudBackupV3,
      appGlobalAuthKeyHwSignature = fullAccountFields.appGlobalAuthKeyHwSignature
    )
  }

  override suspend fun decryptCloudBackup(
    cloudBackupV3: CloudBackupV3,
  ): Result<FullAccountKeys, CloudBackupV3RestorerError> {
    val fullAccountFields = requireNotNull(cloudBackupV3.fullAccountFields)

    val pkek =
      csekDao.get(fullAccountFields.sealedHwEncryptionKey).get()
        ?: return Err(PkekMissingError)

    val keysInfoEncoded =
      catchingResult {
        symmetricKeyEncryptor.unsealNoMetadata(
          sealedData = fullAccountFields.hwFullAccountKeysCiphertext,
          key = pkek.key
        )
      }.getOrElse { return Err(AccountBackupDecryptionError(cause = it)) }
        .utf8()

    return jsonSerializer.decodeFromStringResult<FullAccountKeys>(keysInfoEncoded)
      .mapError {
        AccountBackupDecodingError(it)
      }
  }
}
