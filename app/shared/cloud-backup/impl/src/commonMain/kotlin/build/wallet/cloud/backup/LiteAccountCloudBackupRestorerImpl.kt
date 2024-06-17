package build.wallet.cloud.backup

import build.wallet.account.AccountRepository
import build.wallet.auth.AccountAuthenticator
import build.wallet.auth.AuthTokenDao
import build.wallet.auth.AuthTokenScope
import build.wallet.auth.logAuthFailure
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.LiteAccountConfig
import build.wallet.bitkey.f8e.LiteAccountId
import build.wallet.cloud.backup.RestoreFromBackupError.AccountBackupRestorationError
import build.wallet.cloud.backup.local.CloudBackupDao
import build.wallet.recovery.socrec.SocRecKeysDao
import build.wallet.recovery.socrec.saveKey
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

class LiteAccountCloudBackupRestorerImpl(
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val socRecKeysDao: SocRecKeysDao,
  private val accountAuthenticator: AccountAuthenticator,
  private val authTokenDao: AuthTokenDao,
  private val cloudBackupDao: CloudBackupDao,
  private val accountRepository: AccountRepository,
) : LiteAccountCloudBackupRestorer {
  override suspend fun restoreFromBackup(liteAccountCloudBackup: CloudBackupV2) =
    coroutineBinding {
      require(liteAccountCloudBackup.fullAccountFields == null)

      // Store auth private keys
      appPrivateKeyDao
        .storeAppKeyPair(liteAccountCloudBackup.appRecoveryAuthKeypair)
        .mapError(::AccountBackupRestorationError)
        .bind()

      // Store trusted contact identity key
      socRecKeysDao
        .saveKey(liteAccountCloudBackup.delegatedDecryptionKeypair)
        .mapError(::AccountBackupRestorationError)
        .bind()

      val authData =
        accountAuthenticator
          .appAuth(
            f8eEnvironment = liteAccountCloudBackup.f8eEnvironment,
            appAuthPublicKey = liteAccountCloudBackup.appRecoveryAuthKeypair.publicKey,
            authTokenScope = AuthTokenScope.Recovery
          )
          .logAuthFailure {
            "Error authenticating with recovery auth key when recovering from Lite account cloud backup."
          }
          .mapError(::AccountBackupRestorationError)
          .bind()

      val accountId = LiteAccountId(authData.accountId)
      authTokenDao
        .setTokensOfScope(accountId, authData.authTokens, AuthTokenScope.Recovery)
        .mapError(::AccountBackupRestorationError)
        .bind()

      cloudBackupDao.set(
        accountId = accountId.serverId,
        backup = liteAccountCloudBackup
      )
        .mapError(::AccountBackupRestorationError)
        .bind()

      val account =
        LiteAccount(
          accountId = accountId,
          LiteAccountConfig(
            bitcoinNetworkType = liteAccountCloudBackup.bitcoinNetworkType,
            f8eEnvironment = liteAccountCloudBackup.f8eEnvironment,
            isTestAccount = liteAccountCloudBackup.isTestAccount,
            isUsingSocRecFakes = liteAccountCloudBackup.isUsingSocRecFakes
          ),
          recoveryAuthKey = liteAccountCloudBackup.appRecoveryAuthKeypair.publicKey
        )

      accountRepository.saveAccountAndBeginOnboarding(
        account = account
      )
        .mapError(::AccountBackupRestorationError)
        .bind()

      account
    }
}
