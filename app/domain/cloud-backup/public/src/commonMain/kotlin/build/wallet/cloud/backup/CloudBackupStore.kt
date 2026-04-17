package build.wallet.cloud.backup

import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudStoreAccount
import com.github.michaelbull.result.Result
import okio.ByteString

/**
 * Platform-specific cloud storage abstraction for backup data.
 *
 * Focused on remote backup storage access only.
 *
 * Although this API uses [ByteString], current implementations persist values via key-value
 * stores that are UTF-8 string based. Callers should only pass values that are UTF-8 encoded.
 *
 * This interface remains intentionally agnostic of backup format, serialization, key
 * conventions, and other backup domain logic.
 */
interface CloudBackupStore {
  suspend fun set(
    account: CloudStoreAccount,
    key: String,
    value: ByteString,
  ): Result<Unit, CloudError>

  suspend fun get(
    account: CloudStoreAccount,
    key: String,
  ): Result<ByteString?, CloudError>

  suspend fun remove(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError>

  suspend fun keys(account: CloudStoreAccount): Result<List<String>, CloudError>
}
