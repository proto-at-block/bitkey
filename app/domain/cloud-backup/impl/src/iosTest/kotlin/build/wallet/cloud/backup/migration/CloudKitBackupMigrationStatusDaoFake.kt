package build.wallet.cloud.backup.migration

import build.wallet.bitkey.f8e.AccountId
import build.wallet.cloud.store.iCloudAccount
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CloudKitBackupMigrationStatusDaoFake : CloudKitBackupMigrationStatusDao {
  private val reconciledKeys = mutableSetOf<String>()
  var clearCallCount = 0
    private set
  var setReconciledCallCount = 0
    private set

  override suspend fun isReconciled(
    accountId: AccountId,
    cloudStoreAccount: iCloudAccount,
  ): Result<Boolean, Throwable> =
    Ok(key(accountId, cloudStoreAccount) in reconciledKeys)

  override suspend fun setReconciled(
    accountId: AccountId,
    cloudStoreAccount: iCloudAccount,
  ): Result<Unit, Throwable> {
    setReconciledCallCount += 1
    reconciledKeys += key(accountId, cloudStoreAccount)
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    clearCallCount += 1
    reconciledKeys.clear()
    return Ok(Unit)
  }

  fun reset() {
    reconciledKeys.clear()
    clearCallCount = 0
    setReconciledCallCount = 0
  }

  private fun key(
    accountId: AccountId,
    cloudStoreAccount: iCloudAccount,
  ): String = "${accountId.serverId}:${cloudStoreAccount.ubiquityIdentityToken}"
}
