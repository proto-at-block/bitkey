package build.wallet.cloud.store

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import platform.CloudKit.CKRecord
import platform.CloudKit.CKRecordID
import platform.Foundation.NSError
import platform.Foundation.NSPredicate

class CloudKitDatabaseFake(
  private val fetchError: NSError? = null,
  private val saveError: NSError? = null,
  private val deleteError: NSError? = null,
  private val queryError: NSError? = null,
) : CloudKitDatabase {
  val records = mutableMapOf<String, CKRecord>()

  override suspend fun fetch(recordID: CKRecordID): Result<CKRecord?, NSError> {
    fetchError?.let { return Err(it) }
    return Ok(records[recordID.recordName])
  }

  override suspend fun save(record: CKRecord): Result<Unit, NSError> {
    saveError?.let { return Err(it) }
    records[record.recordID.recordName] = record
    return Ok(Unit)
  }

  override suspend fun delete(recordID: CKRecordID): Result<Unit, NSError> {
    deleteError?.let { return Err(it) }
    records.remove(recordID.recordName)
    return Ok(Unit)
  }

  override suspend fun query(
    recordType: String,
    predicate: NSPredicate,
    desiredKeys: List<String>?,
  ): Result<List<CKRecord>, NSError> {
    queryError?.let { return Err(it) }
    return Ok(records.values.filter { it.recordType == recordType })
  }

  fun reset() {
    records.clear()
  }
}
