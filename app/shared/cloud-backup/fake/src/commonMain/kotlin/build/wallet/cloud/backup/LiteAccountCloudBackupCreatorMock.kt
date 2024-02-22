package build.wallet.cloud.backup

import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.auth.AppRecoveryAuthKeypairMock
import build.wallet.cloud.backup.LiteAccountCloudBackupCreator.LiteAccountCloudBackupCreatorError
import build.wallet.recovery.socrec.TrustedContactIdentityKeyFake
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class LiteAccountCloudBackupCreatorMock : LiteAccountCloudBackupCreator {
  init {
    reset()
  }

  lateinit var createResultCreator:
    (CloudBackupV2) -> Result<CloudBackupV2, LiteAccountCloudBackupCreatorError>

  override suspend fun create(
    account: LiteAccount,
  ): Result<CloudBackupV2, LiteAccountCloudBackupCreatorError> {
    val cloudBackupV2 =
      CloudBackupV2(
        accountId = account.accountId.serverId,
        f8eEnvironment = account.config.f8eEnvironment,
        isTestAccount = account.config.isTestAccount,
        fullAccountFields = null,
        appRecoveryAuthKeypair = AppRecoveryAuthKeypairMock,
        trustedContactIdentityKeypair = TrustedContactIdentityKeyFake,
        isUsingSocRecFakes = account.config.isUsingSocRecFakes,
        bitcoinNetworkType = account.config.bitcoinNetworkType
      )
    return createResultCreator(cloudBackupV2)
  }

  fun reset() {
    createResultCreator = ::Ok
  }
}
