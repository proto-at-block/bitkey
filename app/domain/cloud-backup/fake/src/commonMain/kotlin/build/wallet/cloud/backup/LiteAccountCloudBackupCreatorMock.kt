package build.wallet.cloud.backup

import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.auth.AppRecoveryAuthKeypairMock
import build.wallet.cloud.backup.LiteAccountCloudBackupCreator.LiteAccountCloudBackupCreatorError
import build.wallet.relationships.DelegatedDecryptionKeyFake
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.datetime.Instant

class LiteAccountCloudBackupCreatorMock : LiteAccountCloudBackupCreator {
  init {
    reset()
  }

  lateinit var createResultCreator:
    (CloudBackup) -> Result<CloudBackup, LiteAccountCloudBackupCreatorError>

  override suspend fun create(
    account: LiteAccount,
  ): Result<CloudBackup, LiteAccountCloudBackupCreatorError> {
    val cloudBackupV3 =
      CloudBackupV3(
        accountId = account.accountId.serverId,
        f8eEnvironment = account.config.f8eEnvironment,
        isTestAccount = account.config.isTestAccount,
        fullAccountFields = null,
        appRecoveryAuthKeypair = AppRecoveryAuthKeypairMock,
        delegatedDecryptionKeypair = DelegatedDecryptionKeyFake,
        isUsingSocRecFakes = account.config.isUsingSocRecFakes,
        bitcoinNetworkType = account.config.bitcoinNetworkType,
        deviceNickname = "Fake Device",
        createdAt = Instant.parse("2025-01-01T00:00:00Z")
      )
    return createResultCreator(cloudBackupV3)
  }

  fun reset() {
    createResultCreator = ::Ok
  }
}
