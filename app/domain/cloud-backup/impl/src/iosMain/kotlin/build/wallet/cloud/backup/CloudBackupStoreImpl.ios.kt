package build.wallet.cloud.backup

import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudKeyValueStore
import build.wallet.cloud.store.CloudKitKeyValueStore
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.iCloudAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.IosCloudKitBackupFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logWarn
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.orElse
import com.github.michaelbull.result.recover
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * iOS implementation of [CloudBackupStore] for iCloud-backed remote backup data.
 *
 * Supports [iCloudAccount] accounts only. With [IosCloudKitBackupFeatureFlag] disabled, it delegates to
 * [CloudKeyValueStore]. When enabled, [CloudKitKeyValueStore] is primary; writes/removes are mirrored to
 * KVS on a best-effort basis. Reads fall back to KVS on CloudKit errors and missing keys to preserve
 * migration compatibility, while list operations fall back to KVS only on CloudKit errors.
 */
@BitkeyInject(AppScope::class)
class CloudBackupStoreImpl(
  private val cloudKeyValueStore: CloudKeyValueStore,
  private val cloudKitKeyValueStore: CloudKitKeyValueStore,
  private val iosCloudKitBackupFeatureFlag: IosCloudKitBackupFeatureFlag,
) : CloudBackupStore {
  override suspend fun set(
    account: CloudStoreAccount,
    key: String,
    value: ByteString,
  ): Result<Unit, CloudError> {
    return when (account) {
      is iCloudAccount -> {
        if (iosCloudKitBackupFeatureFlag.isEnabled()) {
          val cloudKitResult = cloudKitKeyValueStore.set(account, key, value)

          // Best-effort mirror to KVS for backwards compatibility during migration.
          cloudKeyValueStore.setString(account, key, value.utf8())
            .onFailure { error ->
              logWarn(throwable = error) { "KVS mirror write failed" }
            }

          cloudKitResult
        } else {
          cloudKeyValueStore.setString(account, key, value.utf8())
        }
      }
      else -> error("Cloud store account type $account is not supported")
    }
  }

  override suspend fun get(
    account: CloudStoreAccount,
    key: String,
  ): Result<ByteString?, CloudError> {
    return when (account) {
      is iCloudAccount -> {
        if (iosCloudKitBackupFeatureFlag.isEnabled()) {
          val cloudKitResult = cloudKitKeyValueStore.get(account, key)
          if (!cloudKitResult.isOk) {
            val cloudKitError = cloudKitResult.error
            cloudKeyValueStore
              .getString(account, key)
              .map { it?.encodeUtf8() }
              // Preserve existing fallback semantics: return CloudKit error if mirror source also fails.
              .mapError { cloudKitError }
          } else if (cloudKitResult.value != null) {
            cloudKitResult
          } else {
            // Preserve migration compatibility by checking KVS when CloudKit key is missing.
            // If KVS also fails in this branch, treat as missing.
            cloudKeyValueStore
              .getString(account, key)
              .map { it?.encodeUtf8() }
              .recover { null }
          }
        } else {
          cloudKeyValueStore.getString(account, key).map { it?.encodeUtf8() }
        }
      }
      else -> error("Cloud store account type $account is not supported")
    }
  }

  override suspend fun remove(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError> {
    return when (account) {
      is iCloudAccount -> {
        if (iosCloudKitBackupFeatureFlag.isEnabled()) {
          val cloudKitResult = cloudKitKeyValueStore.remove(account, key)

          // Best-effort mirror to KVS for backwards compatibility during migration.
          cloudKeyValueStore.removeString(account, key)
            .onFailure { error ->
              logWarn(throwable = error) { "KVS mirror removal failed" }
            }

          cloudKitResult
        } else {
          cloudKeyValueStore.removeString(account, key)
        }
      }
      else -> error("Cloud store account type $account is not supported")
    }
  }

  override suspend fun keys(account: CloudStoreAccount): Result<List<String>, CloudError> {
    return when (account) {
      is iCloudAccount -> {
        if (iosCloudKitBackupFeatureFlag.isEnabled()) {
          // Fallback to KVS only on CloudKit errors, not on empty results.
          cloudKitKeyValueStore.keys(account)
            .map { it.toList() }
            .orElse { cloudKitError ->
              cloudKeyValueStore
                .keys(account)
                // Preserve existing fallback semantics: return CloudKit error if mirror source also fails.
                .mapError { cloudKitError }
            }
        } else {
          cloudKeyValueStore.keys(account)
        }
      }
      else -> error("Cloud store account type $account is not supported")
    }
  }
}
