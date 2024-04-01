package build.wallet.cloud.backup

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import build.wallet.bitkey.socrec.TrustedContact
import build.wallet.bitkey.socrec.TrustedContactAuthenticationState.VERIFIED
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.AppRecoveryAuthKeypairRetrievalError
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.FullAccountFieldsCreationError
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator
import build.wallet.logging.logFailure
import build.wallet.recovery.socrec.SocRecKeysRepository
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse

class FullAccountCloudBackupCreatorImpl(
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val fullAccountFieldsCreator: FullAccountFieldsCreator,
  private val socRecKeysRepository: SocRecKeysRepository,
) : FullAccountCloudBackupCreator {
  override suspend fun create(
    keybox: Keybox,
    sealedCsek: SealedCsek,
    trustedContacts: List<TrustedContact>,
  ): Result<CloudBackup, FullAccountCloudBackupCreatorError> {
    val fullAccountFields = fullAccountFieldsCreator
      .create(
        keybox = keybox,
        sealedCsek = sealedCsek,
        // Exclude trusted contacts that have not been verified.
        trustedContacts = trustedContacts.filter { it.authenticationState == VERIFIED }
      )
      .logFailure { "Error creating full account backup" }
      .getOrElse {
        return Err(FullAccountFieldsCreationError(it))
      }

    val delegatedDecryptionKey =
      socRecKeysRepository.getKeyWithPrivateMaterialOrCreate<DelegatedDecryptionKey>()
        .logFailure { "Error getting delegated decryption key" }
        .getOrElse { return Err(FullAccountCloudBackupCreatorError.SocRecKeysRetrievalError(it)) }

    val appRecoveryAuthKeypair =
      keybox.appRecoveryAuthKeypair(appPrivateKeyDao)
        .logFailure { "Error getting app recovery auth keypair" }
        .getOrElse { return Err(AppRecoveryAuthKeypairRetrievalError(it)) }

    return Ok(
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
    )
  }
}
