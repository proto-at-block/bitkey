package build.wallet.cloud.backup

import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudKeyValueStore
import build.wallet.cloud.store.CloudKitKeyValueStore
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.iCloudAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Impl
import build.wallet.feature.flags.IosCloudKitBackupFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logInfo
import build.wallet.logging.logWarn
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

// CloudKit record names must not start with an underscore.
private const val CLOUD_KIT_POPULATED_MARKER_KEY_PREFIX = "bitkey_cloudkit_populated:"
private const val CLOUD_KIT_POPULATED_MARKER_VALUE = "true"

private data class CloudKitPopulatedKey(
  val account: iCloudAccount,
  val key: String,
)

/**
 * iOS implementation of [CloudBackupStore] for iCloud-backed remote backup data.
 *
 * Supports [iCloudAccount] accounts only. With [IosCloudKitBackupFeatureFlag] disabled, it delegates to
 * [CloudKeyValueStore]. When enabled, [CloudKitKeyValueStore] is primary; writes/removes are mirrored to
 * KVS on a best-effort basis.
 *
 * Read/list operations fall back to KVS when CloudKit returns null/empty **only before** CloudKit
 * has been confirmed to contain data for a backup key. Once any operation proves CloudKit has been
 * populated for a key (a successful write/remove, a non-null read, or a key listing), CloudKit is
 * treated as authoritative for that key's subsequent null responses. The populated signal is
 * persisted as an internal CloudKit marker so this remains true across app restarts. This prevents
 * stale KVS data from resurrecting backups that were intentionally deleted from CloudKit while
 * preserving recovery for other keys in the same shared iCloud account that still have KVS-only
 * backups.
 *
 * On CloudKit errors, KVS fallback always applies regardless of migration state.
 *
 * If CloudKit and KVS both contain a parseable backup for the same key and account, reads return
 * the backup with the later `createdAt`. Equal timestamps keep CloudKit. This preserves rollback
 * recovery for reinstall flows where no local backup database exists yet.
 */
@BitkeyInject(AppScope::class)
@Impl
class CloudBackupStoreImpl(
  @Impl private val cloudKeyValueStore: CloudKeyValueStore,
  private val cloudKitKeyValueStore: CloudKitKeyValueStore,
  private val iosCloudKitBackupFeatureFlag: IosCloudKitBackupFeatureFlag,
  private val jsonSerializer: JsonSerializer,
) : CloudBackupStore {
  /**
   * Tracks backup keys where CloudKit has been confirmed to contain data during this app session,
   * avoiding repeated marker reads. Durable state lives in per-key CloudKit marker records.
   */
  private val cloudKitPopulatedKeys = mutableSetOf<CloudKitPopulatedKey>()
  private val cloudKitPopulatedMarkerKeys = mutableSetOf<CloudKitPopulatedKey>()
  private val cloudKitPopulatedKeysMutex = Mutex()

  private fun cloudKitPopulatedMarkerKey(key: String) = "$CLOUD_KIT_POPULATED_MARKER_KEY_PREFIX$key"

  private fun String.cloudKitPopulatedBackupKey(): String? {
    return if (startsWith(CLOUD_KIT_POPULATED_MARKER_KEY_PREFIX)) {
      removePrefix(CLOUD_KIT_POPULATED_MARKER_KEY_PREFIX)
    } else {
      null
    }
  }

  private suspend fun markCloudKitPopulated(
    account: iCloudAccount,
    key: String,
  ): Result<Unit, CloudError> {
    val populatedKey = CloudKitPopulatedKey(account, key)
    if (isCloudKitPopulatedMarkerCached(populatedKey)) return Ok(Unit)

    cacheCloudKitPopulatedKey(populatedKey)

    return cloudKitKeyValueStore
      .set(
        account = account,
        key = cloudKitPopulatedMarkerKey(key),
        value = CLOUD_KIT_POPULATED_MARKER_VALUE.encodeUtf8()
      )
      .fold(
        success = {
          cacheCloudKitPopulatedMarker(populatedKey)
          Ok(Unit)
        },
        failure = { error ->
          logWarn(throwable = error) { "CloudKit populated marker write failed" }
          Err(error)
        }
      )
  }

  private suspend fun markCloudKitPopulatedKeys(
    account: iCloudAccount,
    keys: List<String>,
  ) {
    keys.forEach { key ->
      markCloudKitPopulated(account, key)
    }
  }

  private suspend fun cloudKitIsPopulated(
    account: iCloudAccount,
    key: String,
  ): Boolean {
    val populatedKey = CloudKitPopulatedKey(account, key)
    if (isCloudKitPopulatedKeyCached(populatedKey)) return true

    return cloudKitKeyValueStore
      .get(account, cloudKitPopulatedMarkerKey(key))
      .fold(
        success = { marker ->
          if (marker != null) {
            cacheCloudKitPopulatedMarker(populatedKey)
            true
          } else {
            false
          }
        },
        failure = { error ->
          logWarn(throwable = error) { "CloudKit populated marker read failed" }
          false
        }
      )
  }

  private suspend fun isCloudKitPopulatedKeyCached(
    populatedKey: CloudKitPopulatedKey,
  ): Boolean {
    return cloudKitPopulatedKeysMutex.withLock {
      populatedKey in cloudKitPopulatedKeys
    }
  }

  private suspend fun isCloudKitPopulatedMarkerCached(
    populatedKey: CloudKitPopulatedKey,
  ): Boolean {
    return cloudKitPopulatedKeysMutex.withLock {
      populatedKey in cloudKitPopulatedMarkerKeys
    }
  }

  private suspend fun cacheCloudKitPopulatedKey(
    populatedKey: CloudKitPopulatedKey,
  ) {
    cloudKitPopulatedKeysMutex.withLock {
      cloudKitPopulatedKeys += populatedKey
    }
  }

  private suspend fun cacheCloudKitPopulatedMarker(
    populatedKey: CloudKitPopulatedKey,
  ) {
    cloudKitPopulatedKeysMutex.withLock {
      cloudKitPopulatedKeys += populatedKey
      cloudKitPopulatedMarkerKeys += populatedKey
    }
  }

  private suspend fun cacheCloudKitPopulatedMarkers(
    account: iCloudAccount,
    keys: Set<String>,
  ) {
    cloudKitPopulatedKeysMutex.withLock {
      val populatedKeys = keys.map { key -> CloudKitPopulatedKey(account, key) }
      cloudKitPopulatedKeys.addAll(populatedKeys)
      cloudKitPopulatedMarkerKeys.addAll(populatedKeys)
    }
  }

  private suspend fun cachedCloudKitPopulatedBackupKeys(
    account: iCloudAccount,
  ): Set<String> {
    return cloudKitPopulatedKeysMutex.withLock {
      cloudKitPopulatedKeys
        .filter { it.account == account }
        .map { it.key }
        .toSet()
    }
  }

  private fun Set<String>.withoutCloudKitPopulatedMarkers(): List<String> {
    return filter { it.cloudKitPopulatedBackupKey() == null }
  }

  private fun Set<String>.cloudKitPopulatedBackupKeys(): Set<String> {
    return mapNotNull { it.cloudKitPopulatedBackupKey() }.toSet()
  }

  private suspend fun setUsingCloudKit(
    account: iCloudAccount,
    key: String,
    value: ByteString,
  ): Result<Unit, CloudError> {
    val result = cloudKitKeyValueStore.set(account, key, value)
      .fold(
        success = { markCloudKitPopulated(account, key) },
        failure = { Err(it) }
      )

    // Best-effort mirror to KVS for backwards compatibility during migration.
    cloudKeyValueStore.setString(account, key, value.utf8())
      .onFailure { error ->
        logWarn(throwable = error) { "KVS mirror write failed" }
      }

    return result
  }

  private suspend fun getUsingCloudKitFallback(
    account: iCloudAccount,
    key: String,
  ): Result<ByteString?, CloudError> {
    return cloudKitKeyValueStore.get(account, key)
      .fold(
        success = { getAfterCloudKitSuccess(account, key, it) },
        failure = { getFromKvsAfterCloudKitError(account, key, it) }
      )
  }

  private suspend fun getAfterCloudKitSuccess(
    account: iCloudAccount,
    key: String,
    cloudKitValue: ByteString?,
  ): Result<ByteString?, CloudError> {
    return if (cloudKitValue != null) {
      markCloudKitPopulated(account, key)
      Ok(freshestCloudKitOrKvsValue(account, key, cloudKitValue))
    } else {
      getAfterCloudKitNull(account, key)
    }
  }

  private suspend fun freshestCloudKitOrKvsValue(
    account: iCloudAccount,
    key: String,
    cloudKitValue: ByteString,
  ): ByteString {
    val kvsValue = cloudKeyValueStore.getString(account, key)
      .onFailure { error ->
        logWarn(throwable = error) { "KVS freshness comparison read failed for key=$key" }
      }
      .getOrElse { null }
      ?.encodeUtf8()
      ?: return cloudKitValue

    return freshestBackupValue(
      cloudKitValue = cloudKitValue,
      kvsValue = kvsValue
    )
  }

  private fun freshestBackupValue(
    cloudKitValue: ByteString,
    kvsValue: ByteString,
  ): ByteString {
    val cloudKitBackup = jsonSerializer.decodeCloudBackup(cloudKitValue.utf8())
      .getOrElse { return cloudKitValue }
    val kvsBackup = jsonSerializer.decodeCloudBackup(kvsValue.utf8())
      .getOrElse { return cloudKitValue }

    return if (
      cloudKitBackup.accountId == kvsBackup.accountId &&
      kvsBackup.isFresherThan(cloudKitBackup)
    ) {
      logInfo { "Using fresher KVS backup over CloudKit backup for account=${kvsBackup.accountId}" }
      kvsValue
    } else {
      cloudKitValue
    }
  }

  private suspend fun getAfterCloudKitNull(
    account: iCloudAccount,
    key: String,
  ): Result<ByteString?, CloudError> {
    return if (cloudKitIsPopulated(account, key)) {
      // Post-migration: CloudKit is authoritative for this key, respect null.
      Ok(null)
    } else {
      getFromKvsPreMigration(account, key)
    }
  }

  private suspend fun getFromKvsAfterCloudKitError(
    account: iCloudAccount,
    key: String,
    cloudKitError: CloudError,
  ): Result<ByteString?, CloudError> {
    // CloudKit error - always fall back to KVS regardless of migration state.
    return cloudKeyValueStore.getString(account, key)
      .map { it?.encodeUtf8() }
      .fold(
        success = { Ok(it) },
        failure = { Err(cloudKitError) }
      )
  }

  private suspend fun getFromKvsPreMigration(
    account: iCloudAccount,
    key: String,
  ): Result<ByteString?, CloudError> {
    logInfo { "CloudKit returned null for key=$key pre-migration; trying KVS fallback" }
    return cloudKeyValueStore.getString(account, key)
      .map { it?.encodeUtf8() }
      .onFailure { error ->
        logWarn(throwable = error) { "KVS fallback read failed for key=$key" }
      }
      .getOrElse { null }
      .let { Ok(it) }
  }

  private suspend fun removeUsingCloudKit(
    account: iCloudAccount,
    key: String,
  ): Result<Unit, CloudError> {
    val cloudKitResult = cloudKitKeyValueStore.remove(account, key)
      .fold(
        success = { markCloudKitPopulated(account, key) },
        failure = { Err(it) }
      )

    // Best-effort mirror to KVS for backwards compatibility during migration.
    cloudKeyValueStore.removeString(account, key)
      .onFailure { error ->
        logWarn(throwable = error) { "KVS mirror removal failed" }
      }

    return cloudKitResult
  }

  private suspend fun keysUsingCloudKitFallback(
    account: iCloudAccount,
  ): Result<List<String>, CloudError> {
    return cloudKitKeyValueStore.keys(account)
      .fold(
        success = { keysAfterCloudKitSuccess(account, it) },
        failure = { keysFromKvsAfterCloudKitError(account, it) }
      )
  }

  private suspend fun keysAfterCloudKitSuccess(
    account: iCloudAccount,
    cloudKitKeys: Set<String>,
  ): Result<List<String>, CloudError> {
    val cloudKitBackupKeys = cloudKitKeys.withoutCloudKitPopulatedMarkers()
    val markerBackupKeys = cloudKitKeys.cloudKitPopulatedBackupKeys()
    cacheCloudKitPopulatedMarkers(account, markerBackupKeys)

    markCloudKitPopulatedKeys(account, cloudKitBackupKeys)
    val cachedBackupKeys = cachedCloudKitPopulatedBackupKeys(account)

    return if (
      cloudKitBackupKeys.isEmpty() &&
      markerBackupKeys.isEmpty() &&
      cachedBackupKeys.isEmpty()
    ) {
      keysFromKvsPreMigration(account)
    } else {
      keysFromCloudKitAndUnmigratedKvs(
        account = account,
        cloudKitBackupKeys = cloudKitBackupKeys,
        markerBackupKeys = markerBackupKeys + cachedBackupKeys
      )
    }
  }

  private suspend fun keysFromCloudKitAndUnmigratedKvs(
    account: iCloudAccount,
    cloudKitBackupKeys: List<String>,
    markerBackupKeys: Set<String>,
  ): Result<List<String>, CloudError> {
    val cloudKitAuthoritativeKeys = cloudKitBackupKeys.toSet() + markerBackupKeys
    val kvsOnlyKeys = cloudKeyValueStore.keys(account)
      .onFailure { error ->
        logWarn(throwable = error) { "KVS fallback keys lookup failed" }
      }
      .getOrElse { emptyList() }
      .filterNot { it in cloudKitAuthoritativeKeys }

    return Ok(cloudKitBackupKeys + kvsOnlyKeys)
  }

  private suspend fun keysFromKvsAfterCloudKitError(
    account: iCloudAccount,
    cloudKitError: CloudError,
  ): Result<List<String>, CloudError> {
    // CloudKit error - always fall back to KVS regardless of migration state.
    return cloudKeyValueStore.keys(account)
      .fold(
        success = { Ok(it) },
        failure = { Err(cloudKitError) }
      )
  }

  private suspend fun keysFromKvsPreMigration(
    account: iCloudAccount,
  ): Result<List<String>, CloudError> {
    logInfo { "CloudKit returned empty keys pre-migration; trying KVS fallback" }
    return cloudKeyValueStore.keys(account)
      .onFailure { error ->
        logWarn(throwable = error) { "KVS fallback keys lookup failed" }
      }
      .getOrElse { emptyList() }
      .let { Ok(it) }
  }

  override suspend fun set(
    account: CloudStoreAccount,
    key: String,
    value: ByteString,
  ): Result<Unit, CloudError> {
    return when (account) {
      is iCloudAccount -> {
        if (iosCloudKitBackupFeatureFlag.isEnabled()) {
          setUsingCloudKit(account, key, value)
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
          getUsingCloudKitFallback(account, key)
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
          removeUsingCloudKit(account, key)
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
          keysUsingCloudKitFallback(account)
        } else {
          cloudKeyValueStore.keys(account)
        }
      }
      else -> error("Cloud store account type $account is not supported")
    }
  }
}
