package build.wallet.cloud.backup

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.relationships.DelegatedDecryptionKey
import build.wallet.bitkey.relationships.TrustedContactAuthenticationState.VERIFIED
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError
import build.wallet.cloud.backup.FullAccountCloudBackupCreator.FullAccountCloudBackupCreatorError.*
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator
import build.wallet.cloud.backup.v2.FullAccountFieldsCreator.FullAccountFieldsCreationError.PkekUnavailableError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.logging.logWarn
import build.wallet.relationships.RelationshipsKeysRepository
import build.wallet.relationships.RelationshipsService
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure

@BitkeyInject(AppScope::class)
class FullAccountCloudBackupCreatorImpl(
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val fullAccountFieldsCreator: FullAccountFieldsCreator,
  private val relationshipsKeysRepository: RelationshipsKeysRepository,
  private val relationshipsService: RelationshipsService,
) : FullAccountCloudBackupCreator {
  override suspend fun create(
    keybox: Keybox,
    sealedCsek: SealedCsek,
  ): Result<CloudBackup, FullAccountCloudBackupCreatorError> =
    coroutineBinding {
      val endorsedTrustedContacts = relationshipsService
        .syncAndVerifyRelationships(
          accountId = keybox.fullAccountId,
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
        .onFailure { error ->
          if (error is PkekUnavailableError) {
            logWarn {
              "No PKEK available for cloud backup. User has no hardware paired. Backup cannot be completed at this time."
            }
          } else {
            logError(throwable = error) { "Unexpected error creating full account backup" }
          }
        }
        .mapError(::FullAccountFieldsCreationError)
        .bind()

      val delegatedDecryptionKey =
        relationshipsKeysRepository.getKeyWithPrivateMaterialOrCreate<DelegatedDecryptionKey>()
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
