package build.wallet.cloud.backup

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.VERIFIED
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.AppRecoveryAuthKeypairRetrievalError
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.FullAccountFieldsCreationError
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.SocRecKeysRetrievalError
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.SocRecVerificationError
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator
import build.wallet.logging.logFailure
import build.wallet.recovery.socrec.SocRecKeysRepository
import build.wallet.recovery.socrec.SocRecService
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

class FullAccountCloudBackupCreatorImpl(
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val fullAccountFieldsCreator: FullAccountFieldsCreator,
  private val socRecKeysRepository: SocRecKeysRepository,
  private val socRecService: SocRecService,
) : FullAccountCloudBackupCreator {
  override suspend fun create(
    keybox: Keybox,
    sealedCsek: SealedCsek,
  ): Result<CloudBackup, FullAccountCloudBackupCreatorError> =
    coroutineBinding {
      val endorsedTrustedContacts = socRecService
        .syncAndVerifyRelationships(
          accountId = keybox.fullAccountId,
          f8eEnvironment = keybox.config.f8eEnvironment,
          appAuthKey = keybox.activeAppKeyBundle.authKey,
          hwAuthPublicKey = keybox.activeHwKeyBundle.authKey
        )
        .mapError(::SocRecVerificationError)
        .bind()
        .endorsedTrustedContacts

      val fullAccountFields = fullAccountFieldsCreator
        .create(
          keybox = keybox,
          sealedCsek = sealedCsek,
          // Exclude trusted contacts that have not been verified.
          endorsedTrustedContacts = endorsedTrustedContacts.filter { it.authenticationState == VERIFIED }
        )
        .logFailure { "Error creating full account backup" }
        .mapError(::FullAccountFieldsCreationError)
        .bind()

      val delegatedDecryptionKey =
        socRecKeysRepository.getKeyWithPrivateMaterialOrCreate<DelegatedDecryptionKey>()
          .logFailure { "Error getting delegated decryption key" }
          .mapError(::SocRecKeysRetrievalError)
          .bind()

      val appRecoveryAuthKeypair =
        keybox.appRecoveryAuthKeypair(appPrivateKeyDao)
          .logFailure { "Error getting app recovery auth keypair" }
          .mapError(::AppRecoveryAuthKeypairRetrievalError)
          .bind()

      CloudBackupV2(
        accountId = keybox.fullAccountId.serverId,
        f8eEnvironment = keybox.config.f8eEnvironment,
        isTestAccount = keybox.config.isTestAccount,
        delegatedDecryptionKeypair = delegatedDecryptionKey,
        fullAccountFields = fullAccountFields,
        appRecoveryAuthKeypair = appRecoveryAuthKeypair,
        isUsingSocRecFakes = keybox.config.isUsingSocRecFakes,
        bitcoinNetworkType = keybox.config.bitcoinNetworkType
      )
    }
}
