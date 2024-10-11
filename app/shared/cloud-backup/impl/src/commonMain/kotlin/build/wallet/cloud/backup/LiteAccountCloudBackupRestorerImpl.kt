package build.wallet.cloud.backup

import build.wallet.account.AccountService
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
import build.wallet.relationships.RelationshipsKeysDao
import build.wallet.relationships.saveKey
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError

class LiteAccountCloudBackupRestorerImpl(
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val relationshipsKeysDao: RelationshipsKeysDao,
  private val accountAuthenticator: AccountAuthenticator,
  private val authTokenDao: AuthTokenDao,
  private val cloudBackupDao: CloudBackupDao,
  private val accountService: AccountService,
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
      relationshipsKeysDao
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

      accountService.saveAccountAndBeginOnboarding(
        account = account
      )
        .mapError(::AccountBackupRestorationError)
        .bind()

      account
    }
}
