package build.wallet.cloud.backup.v2

import bitkey.account.FullAccountConfig
import bitkey.serialization.json.decodeFromStringResult
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.catchingResult
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.cloud.backup.CloudBackupV2Restorer
import build.wallet.cloud.backup.CloudBackupV2Restorer.CloudBackupV2RestorerError
import build.wallet.cloud.backup.CloudBackupV2Restorer.CloudBackupV2RestorerError.*
import build.wallet.cloud.backup.FullAccountCloudBackupRestorer.AccountRestoration
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SymmetricKeyEncryptor
import build.wallet.platform.random.UuidGenerator
import build.wallet.relationships.RelationshipsKeysDao
import build.wallet.relationships.saveKey
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json

@BitkeyInject(AppScope::class)
class CloudBackupV2RestorerImpl(
  private val csekDao: CsekDao,
  private val symmetricKeyEncryptor: SymmetricKeyEncryptor,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val uuidGenerator: UuidGenerator,
  private val relationshipsKeysDao: RelationshipsKeysDao,
) : CloudBackupV2Restorer {
  override suspend fun restore(
    cloudBackupV2: CloudBackupV2,
  ): Result<AccountRestoration, CloudBackupV2RestorerError> {
    val fullAccountFields = requireNotNull(cloudBackupV2.fullAccountFields)

    val pkek =
      csekDao.get(fullAccountFields.sealedHwEncryptionKey).get()
        ?: return Err(PkekMissingError)

    val keysInfoEncoded =
      catchingResult {
        symmetricKeyEncryptor.unseal(
          sealedData = fullAccountFields.hwFullAccountKeysCiphertext,
          key = pkek.key
        )
      }.getOrElse { return Err(AccountBackupDecryptionError(cause = it)) }
        .utf8()

    val keysInfo =
      Json
        .decodeFromStringResult<FullAccountKeys>(keysInfoEncoded)
        .mapError {
          AccountBackupDecodingError(it)
        }
        .getOrElse { return Err(it) }

    return restoreWithDecryptedKeys(cloudBackupV2, keysInfo)
  }

  override suspend fun restoreWithDecryptedKeys(
    cloudBackupV2: CloudBackupV2,
    keysInfo: FullAccountKeys,
  ) = coroutineBinding {
    val fullAccountFields = requireNotNull(cloudBackupV2.fullAccountFields)

    // Store auth private keys
    appPrivateKeyDao.storeAppKeyPair(cloudBackupV2.appRecoveryAuthKeypair).mapError {
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
    relationshipsKeysDao.saveKey(cloudBackupV2.delegatedDecryptionKeypair)
      .mapError(::SocRecTrustedContactIdentityKeyStorageError)
      .bind()

    AccountRestoration(
      activeSpendingKeyset = keysInfo.activeSpendingKeyset,
      inactiveKeysets = keysInfo.inactiveSpendingKeysets.toImmutableList(),
      activeAppKeyBundle =
        AppKeyBundle(
          localId = uuidGenerator.random(),
          spendingKey = keysInfo.activeSpendingKeyset.appKey,
          authKey = keysInfo.appGlobalAuthKeypair.publicKey,
          networkType = cloudBackupV2.bitcoinNetworkType,
          recoveryAuthKey = cloudBackupV2.appRecoveryAuthKeypair.publicKey
        ),
      activeHwKeyBundle = HwKeyBundle(
        localId = uuidGenerator.random(),
        spendingKey = keysInfo.activeHwSpendingKey,
        authKey = keysInfo.activeHwAuthKey,
        networkType = cloudBackupV2.bitcoinNetworkType
      ),
      config =
        FullAccountConfig(
          bitcoinNetworkType = cloudBackupV2.bitcoinNetworkType,
          f8eEnvironment = cloudBackupV2.f8eEnvironment,
          isHardwareFake = fullAccountFields.isFakeHardware,
          isTestAccount = cloudBackupV2.isTestAccount,
          isUsingSocRecFakes = cloudBackupV2.isUsingSocRecFakes
        ),
      cloudBackupForLocalStorage = cloudBackupV2,
      appGlobalAuthKeyHwSignature = fullAccountFields.appGlobalAuthKeyHwSignature
    )
  }
}
