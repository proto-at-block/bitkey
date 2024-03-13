package build.wallet.cloud.backup

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.app.AppRecoveryAuthKeypair
import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import build.wallet.cloud.backup.LiteAccountCloudBackupCreator.LiteAccountCloudBackupCreatorError.AppRecoveryAuthKeypairRetrievalError
import build.wallet.cloud.backup.LiteAccountCloudBackupCreator.LiteAccountCloudBackupCreatorError.SocRecKeysRetrievalError
import build.wallet.recovery.socrec.SocRecKeysRepository
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull

class LiteAccountCloudBackupCreatorImpl(
  private val socRecKeysRepository: SocRecKeysRepository,
  private val appPrivateKeyDao: AppPrivateKeyDao,
) : LiteAccountCloudBackupCreator {
  override suspend fun create(
    account: LiteAccount,
  ): Result<CloudBackupV2, LiteAccountCloudBackupCreator.LiteAccountCloudBackupCreatorError> =
    binding {
      val delegatedDecryptionKeypair =
        socRecKeysRepository.getKeyWithPrivateMaterialOrCreate(::DelegatedDecryptionKey)
          .mapError { SocRecKeysRetrievalError(it) }
          .bind()

      val recoveryAuthPrivateKey =
        appPrivateKeyDao
          .getRecoveryAuthKey(account.recoveryAuthKey)
          .toErrorIfNull {
            IllegalStateException("Active recovery app auth private key not found.")
          }
          .mapError { AppRecoveryAuthKeypairRetrievalError(it) }
          .bind()

      val appRecoveryAuthKeypair =
        AppRecoveryAuthKeypair(
          publicKey = account.recoveryAuthKey,
          privateKey = recoveryAuthPrivateKey
        )

      CloudBackupV2(
        accountId = account.accountId.serverId,
        f8eEnvironment = account.config.f8eEnvironment,
        isTestAccount = account.config.isTestAccount,
        fullAccountFields = null,
        appRecoveryAuthKeypair = appRecoveryAuthKeypair,
        delegatedDecryptionKeypair = delegatedDecryptionKeypair,
        isUsingSocRecFakes = account.config.isUsingSocRecFakes,
        bitcoinNetworkType = account.config.bitcoinNetworkType
      )
    }
}
