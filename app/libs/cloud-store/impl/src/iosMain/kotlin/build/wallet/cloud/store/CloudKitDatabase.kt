package build.wallet.cloud.store

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CloudKit.CKContainer
import platform.CloudKit.CKDatabase
import platform.CloudKit.CKQueryCursor
import platform.CloudKit.CKRecord
import platform.CloudKit.CKRecordID
import platform.CloudKit.deleteRecordWithID
import platform.CloudKit.fetchRecordWithID
import platform.CloudKit.privateCloudDatabase
import platform.CloudKit.saveRecord
import platform.Foundation.NSError
import platform.Foundation.NSPredicate
import kotlin.coroutines.resume

/**
 * Adapter around CloudKit database operations.
 *
 * This exists to push CloudKit's callback API to the edge of the module and let the rest of
 * the codebase speak in `suspend` + `Result`. It intentionally keeps [CKRecord] and [NSError]
 * visible so higher layers can make CloudKit-specific decisions (unknown-item, conflicts, etc.)
 * without losing fidelity.
 *
 * The default implementation targets the default container's private database, which is
 * already scoped to the signed-in iCloud account. No retries, backoff, or conflict handling
 * live here; those are deliberate choices at the call site.
 */
interface CloudKitDatabase {
  /**
   * Fetch a record by its ID.
   *
   * @return [Ok] with the record on success. Missing records and other failures are returned
   *         as [Err] with the underlying [NSError] (for example, CKErrorUnknownItem).
   */
  suspend fun fetch(recordID: CKRecordID): Result<CKRecord?, NSError>

  /**
   * Save (create or update) a record.
   *
   * @return [Ok] on success, [Err] with the underlying [NSError].
   */
  suspend fun save(record: CKRecord): Result<Unit, NSError>

  /**
   * Delete a record by its ID.
   *
   * @return [Ok] on success, [Err] with the underlying [NSError].
   */
  suspend fun delete(recordID: CKRecordID): Result<Unit, NSError>

  /**
   * Query records by type and predicate.
   *
   * @param desiredKeys Optional list of record fields to fetch. When empty, CloudKit returns only
   *                    record metadata (record IDs) without payload fields.
   * @param predicate Predicate for filtering records. Defaults to returning all records.
   * @return [Ok] with records, [Err] with the underlying [NSError].
   */
  suspend fun query(
    recordType: String,
    predicate: NSPredicate = NSPredicate.predicateWithValue(true),
    desiredKeys: List<String>? = null,
  ): Result<List<CKRecord>, NSError>
}

/**
 * Default [CloudKitDatabase] backed by the default container's private database.
 */
@BitkeyInject(AppScope::class)
class CloudKitDatabaseImpl(
  private val queryRunner: CloudKitQueryRunner,
) : CloudKitDatabase {
  private val database: CKDatabase by lazy { CKContainer.defaultContainer().privateCloudDatabase }

  override suspend fun fetch(recordID: CKRecordID): Result<CKRecord?, NSError> =
    suspendCancellableCoroutine { continuation ->
      database.fetchRecordWithID(recordID) { record, error ->
        if (!continuation.isActive) return@fetchRecordWithID
        if (error != null) {
          continuation.resume(Err(error))
        } else {
          continuation.resume(Ok(record))
        }
      }
    }

  override suspend fun save(record: CKRecord): Result<Unit, NSError> =
    suspendCancellableCoroutine { continuation ->
      database.saveRecord(record) { _, error ->
        if (!continuation.isActive) return@saveRecord
        if (error != null) {
          continuation.resume(Err(error))
        } else {
          continuation.resume(Ok(Unit))
        }
      }
    }

  override suspend fun delete(recordID: CKRecordID): Result<Unit, NSError> =
    suspendCancellableCoroutine { continuation ->
      database.deleteRecordWithID(recordID) { _, error ->
        if (!continuation.isActive) return@deleteRecordWithID
        if (error != null) {
          continuation.resume(Err(error))
        } else {
          continuation.resume(Ok(Unit))
        }
      }
    }

  override suspend fun query(
    recordType: String,
    predicate: NSPredicate,
    desiredKeys: List<String>?,
  ): Result<List<CKRecord>, NSError> {
    val records = mutableListOf<CKRecord>()
    var cursor: CKQueryCursor? = null

    do {
      val page = queryPage(recordType, predicate, cursor, desiredKeys)
        .getOrElse { return Err(it) }
      records.addAll(page.records)
      cursor = page.cursor
    } while (cursor != null)

    return Ok(records)
  }

  private suspend fun queryPage(
    recordType: String,
    predicate: NSPredicate,
    cursor: CKQueryCursor?,
    desiredKeys: List<String>?,
  ): Result<CloudKitQueryPage, NSError> {
    return queryRunner.queryPage(
      database = database,
      recordType = recordType,
      predicate = predicate,
      cursor = cursor,
      desiredKeys = desiredKeys
    ).toResult()
  }
}
