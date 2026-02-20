package build.wallet.cloud.store

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.IosCloudKitBackupFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logWarn
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.recover
import okio.ByteString.Companion.encodeUtf8

@BitkeyInject(AppScope::class)
class CloudKeyValueStoreImpl(
  private val ubiquitousKeyValueStore: UbiquitousKeyValueStore,
  private val cloudKitKeyValueStore: CloudKitKeyValueStore,
  private val iosCloudKitBackupFeatureFlag: IosCloudKitBackupFeatureFlag,
) : CloudKeyValueStore {
  override suspend fun setString(
    account: CloudStoreAccount,
    key: String,
    value: String,
  ): Result<Unit, CloudError> {
    return when (account) {
      is iCloudAccount -> {
        if (!iosCloudKitBackupFeatureFlag.isEnabled()) {
          ubiquitousKeyValueStore.setString(account, key, value)
        } else {
          val cloudKitResult = cloudKitKeyValueStore.set(account, key, value.encodeUtf8())

          // Best-effort mirror to KVS for backwards compatibility during migration
          ubiquitousKeyValueStore.setString(account, key, value)
            .onFailure { error ->
              logWarn(throwable = error) { "KVS mirror write failed" }
            }

          cloudKitResult
        }
      }
      else -> error("Cloud store account type $account is not supported")
    }
  }

  override suspend fun getString(
    account: CloudStoreAccount,
    key: String,
  ): Result<String?, CloudError> {
    return when (account) {
      is iCloudAccount -> {
        if (!iosCloudKitBackupFeatureFlag.isEnabled()) {
          ubiquitousKeyValueStore.getString(account, key)
        } else {
          // Fallback to KVS only on CloudKit errors, not on "not found" (null) results.
          // Once CloudKit is authoritative, intentional deletions should be respected.
          cloudKitKeyValueStore.get(account, key)
            .map { it?.utf8() }
            .recover { cloudKitError ->
              ubiquitousKeyValueStore.getString(account, key)
                .getOrElse { return Err(cloudKitError) }
            }
        }
      }
      else -> error("Cloud store account type $account is not supported")
    }
  }

  override suspend fun removeString(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError> {
    return when (account) {
      is iCloudAccount -> {
        if (!iosCloudKitBackupFeatureFlag.isEnabled()) {
          ubiquitousKeyValueStore.removeString(account, key)
        } else {
          val cloudKitResult = cloudKitKeyValueStore.remove(account, key)

          // Best-effort mirror to KVS for backwards compatibility during migration
          ubiquitousKeyValueStore.removeString(account, key)
            .onFailure { error ->
              logWarn(throwable = error) { "KVS mirror removal failed" }
            }

          cloudKitResult
        }
      }
      else -> error("Cloud store account type $account is not supported")
    }
  }

  override suspend fun keys(account: CloudStoreAccount): Result<List<String>, CloudError> {
    return when (account) {
      is iCloudAccount -> {
        if (!iosCloudKitBackupFeatureFlag.isEnabled()) {
          ubiquitousKeyValueStore.keys(account)
        } else {
          // Fallback to KVS only on CloudKit errors, not on empty results.
          cloudKitKeyValueStore.keys(account)
            .map { it.toList() }
            .recover { cloudKitError ->
              ubiquitousKeyValueStore.keys(account)
                .getOrElse { return Err(cloudKitError) }
            }
        }
      }
      else -> error("Cloud store account type $account is not supported")
    }
  }
}
