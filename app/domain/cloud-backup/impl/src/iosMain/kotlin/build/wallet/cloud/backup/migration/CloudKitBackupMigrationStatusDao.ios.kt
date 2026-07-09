package build.wallet.cloud.backup.migration

import build.wallet.bitkey.f8e.AccountId
import build.wallet.cloud.store.iCloudAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.store.KeyValueStoreFactory
import build.wallet.store.clearWithResult
import build.wallet.store.getStringOrNullWithResult
import build.wallet.store.putStringWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.russhwolf.settings.coroutines.SuspendSettings
import okio.ByteString.Companion.encodeUtf8

interface CloudKitBackupMigrationStatusDao {
  suspend fun isReconciled(
    accountId: AccountId,
    cloudStoreAccount: iCloudAccount,
  ): Result<Boolean, Throwable>

  suspend fun setReconciled(
    accountId: AccountId,
    cloudStoreAccount: iCloudAccount,
  ): Result<Unit, Throwable>

  suspend fun clear(): Result<Unit, Throwable>
}

@BitkeyInject(AppScope::class)
class CloudKitBackupMigrationStatusDaoImpl(
  private val keyValueStoreFactory: KeyValueStoreFactory,
) : CloudKitBackupMigrationStatusDao {
  private suspend fun store(): SuspendSettings =
    keyValueStoreFactory.getOrCreate(STORE_NAME)

  override suspend fun isReconciled(
    accountId: AccountId,
    cloudStoreAccount: iCloudAccount,
  ): Result<Boolean, Throwable> =
    store()
      .getStringOrNullWithResult(reconciledKey(accountId, cloudStoreAccount))
      .map { value -> value == RECONCILED_VALUE }

  override suspend fun setReconciled(
    accountId: AccountId,
    cloudStoreAccount: iCloudAccount,
  ): Result<Unit, Throwable> =
    store().putStringWithResult(
      key = reconciledKey(accountId, cloudStoreAccount),
      value = RECONCILED_VALUE
    )

  override suspend fun clear(): Result<Unit, Throwable> =
    store().clearWithResult()

  private fun reconciledKey(
    accountId: AccountId,
    cloudStoreAccount: iCloudAccount,
  ): String {
    val markerMaterial = "${accountId.serverId}:${cloudStoreAccount.ubiquityIdentityToken}"
    return "active-reconciled:${markerMaterial.encodeUtf8().sha256().hex()}"
  }

  private companion object {
    const val STORE_NAME = "CloudKitBackupMigrationStatus"
    const val RECONCILED_VALUE = "true"
  }
}
