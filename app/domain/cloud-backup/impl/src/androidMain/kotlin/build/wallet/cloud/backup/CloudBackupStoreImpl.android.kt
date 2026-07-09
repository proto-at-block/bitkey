package build.wallet.cloud.backup

import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.GoogleAccount
import build.wallet.cloud.store.GoogleDriveKeyValueStore
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Impl
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * Android implementation of [CloudBackupStore].
 *
 * Uses [GoogleDriveKeyValueStore] as the remote backup backend and supports [GoogleAccount] accounts only.
 * Values are stored as UTF-8 strings in Google Drive and converted to/from [ByteString] at this boundary.
 */
@BitkeyInject(AppScope::class)
@Impl
class CloudBackupStoreImpl(
  private val googleDriveKeyValueStore: GoogleDriveKeyValueStore,
) : CloudBackupStore {
  override suspend fun set(
    account: CloudStoreAccount,
    key: String,
    value: ByteString,
  ): Result<Unit, CloudError> {
    return when (account) {
      is GoogleAccount -> googleDriveKeyValueStore.setString(account, key, value.utf8())
      else -> error("Cloud store account $account is not supported.")
    }
  }

  override suspend fun get(
    account: CloudStoreAccount,
    key: String,
  ): Result<ByteString?, CloudError> {
    return when (account) {
      is GoogleAccount -> googleDriveKeyValueStore.getString(account, key).map { it?.encodeUtf8() }
      else -> error("Cloud store account $account is not supported.")
    }
  }

  override suspend fun remove(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError> {
    return when (account) {
      is GoogleAccount -> googleDriveKeyValueStore.remove(account, key)
      else -> error("Cloud store account $account is not supported.")
    }
  }

  override suspend fun keys(account: CloudStoreAccount): Result<List<String>, CloudError> {
    return when (account) {
      is GoogleAccount -> googleDriveKeyValueStore.keys(account)
      else -> error("Cloud store account $account is not supported.")
    }
  }
}
