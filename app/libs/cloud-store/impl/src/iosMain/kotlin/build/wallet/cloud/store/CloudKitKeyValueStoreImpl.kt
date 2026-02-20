package build.wallet.cloud.store

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.recoverIf
import com.rickclephas.kmp.nserrorkt.asThrowable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import okio.ByteString
import okio.ByteString.Companion.toByteString
import platform.CloudKit.CKErrorDomain
import platform.CloudKit.CKErrorUnknownItem
import platform.CloudKit.CKRecord
import platform.CloudKit.CKRecordID
import platform.CloudKit.CKRecordValueProtocol
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create

/**
 * CloudKit-based key-value store implementation for iOS.
 *
 * Think of this as "bytes in / bytes out" storage with CloudKit doing the syncing.
 *
 * Storage model:
 * - Private database in the default iCloud container (scoped to the active iCloud account).
 * - One record per key; recordID.recordName == key.
 * - Record type [RECORD_TYPE] with a single payload field [FIELD_VALUE] (NSData).
 *
 * Operational choices (the non-obvious bits):
 * - Missing records are treated as not found (unknown-item error -> null/Ok(Unit)),
 *   keeping reads/removes idempotent and callers simple.
 * - `set` does a fetch-then-save. CloudKit "save" is an upsert, but updates expect a
 *   change tag; fetching first preserves the change tag and avoids server-change errors.
 * - [iCloudAccount] is accepted for interface parity with KVS but isn't used directly
 *   because CloudKit's private database is already scoped to the signed-in account.
 *
 * Used by [CloudKeyValueStoreImpl] when [IosCloudKitBackupFeatureFlag] is enabled,
 * with [UbiquitousKeyValueStore] (iCloud KVS) as fallback during the migration period.
 */
@Suppress("unused", "ClassName")
@BitkeyInject(AppScope::class)
class CloudKitKeyValueStoreImpl(
  private val cloudKitDatabase: CloudKitDatabase,
) : CloudKitKeyValueStore {
  override suspend fun set(
    account: iCloudAccount,
    key: String,
    value: ByteString,
  ): Result<Unit, CloudError> {
    val recordId = recordIdForKey(key)
    val recordResult = cloudKitDatabase.fetch(recordId)
      .recoverIf({ it.isUnknownItemError() }) { null }
      .mapError { it.toCloudError() }

    val record = recordResult.getOrElse { return Err(it) }
      ?: CKRecord(recordType = RECORD_TYPE, recordID = recordId)

    record.setObject(value.toNSData() as CKRecordValueProtocol, forKey = FIELD_VALUE)

    return cloudKitDatabase.save(record)
      .mapError { it.toCloudError() }
  }

  override suspend fun get(
    account: iCloudAccount,
    key: String,
  ): Result<ByteString?, CloudError> {
    val recordId = recordIdForKey(key)
    val recordResult = cloudKitDatabase.fetch(recordId)
      .recoverIf({ it.isUnknownItemError() }) { null }
      .mapError { it.toCloudError() }
    val record = recordResult.getOrElse { return Err(it) }
    val data = record?.valueData() ?: return Ok(null)
    return data.toByteStringResult()
      .map { it as ByteString? }
  }

  override suspend fun remove(
    account: iCloudAccount,
    key: String,
  ): Result<Unit, CloudError> {
    val recordId = recordIdForKey(key)
    return cloudKitDatabase.delete(recordId)
      .recoverIf({ it.isUnknownItemError() }) { Unit }
      .mapError { it.toCloudError() }
  }

  override suspend fun keys(account: iCloudAccount): Result<Set<String>, CloudError> {
    return cloudKitDatabase.query(
      recordType = RECORD_TYPE,
      desiredKeys = emptyList()
    )
      .map { records -> records.map { it.recordID.recordName }.toSet() }
      .mapError { it.toCloudError() }
  }

  private fun recordIdForKey(key: String): CKRecordID = CKRecordID(recordName = key)

  private fun CKRecord.valueData(): NSData? = objectForKey(FIELD_VALUE) as? NSData

  private fun NSError.toCloudError(): CloudKitKeyValueStoreError {
    val message = localizedDescription
      ?: description
      ?: "CloudKit error (domain=$domain code=$code)"
    return CloudKitKeyValueStoreError(
      message = message,
      cause = asThrowable(),
      rectificationData = this
    )
  }

  private fun NSError.isUnknownItemError(): Boolean =
    domain == CKErrorDomain && code == CKErrorUnknownItem

  @OptIn(ExperimentalForeignApi::class)
  private fun ByteString.toNSData(): NSData =
    memScoped {
      NSData.create(
        bytes = allocArrayOf(toByteArray()),
        length = size.toULong()
      )
    }

  @OptIn(ExperimentalForeignApi::class)
  private fun NSData.toByteStringResult(): Result<ByteString, CloudError> {
    val byteCount = length.toInt()
    if (byteCount == 0) return Ok(ByteString.EMPTY)
    val byteArray = bytes?.readBytes(byteCount)
      ?: return Err(
        CloudKitKeyValueStoreError(
          message = "CloudKit returned invalid NSData (length=$byteCount)",
          cause = IllegalStateException("CloudKit returned invalid NSData (length=$byteCount)"),
          rectificationData = this
        )
      )
    return Ok(byteArray.toByteString())
  }

  private companion object {
    private const val RECORD_TYPE = "CloudKeyValue"
    private const val FIELD_VALUE = "value"
  }
}
