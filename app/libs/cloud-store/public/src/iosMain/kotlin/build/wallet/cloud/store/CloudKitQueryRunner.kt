package build.wallet.cloud.store

import com.github.michaelbull.result.Result
import platform.CloudKit.CKDatabase
import platform.CloudKit.CKQueryCursor
import platform.CloudKit.CKRecord
import platform.Foundation.NSError
import platform.Foundation.NSPredicate
import com.github.michaelbull.result.Err as ResultErr
import com.github.michaelbull.result.Ok as ResultOk

/**
 * iOS-only CloudKit query adapter to access Swift-only APIs (queryResultBlock).
 *
 * Kotlin/Native only exposes Objective-C APIs. CloudKit's `queryResultBlock` is Swift-only,
 * so we bridge it through this interface and implement it in Swift.
 */
interface CloudKitQueryRunner {
  /**
   * Fetch one page of query results, returning records and an optional cursor for the next page.
   */
  suspend fun queryPage(
    database: CKDatabase,
    recordType: String,
    predicate: NSPredicate = NSPredicate.predicateWithValue(true),
    cursor: CKQueryCursor? = null,
    desiredKeys: List<String>? = null,
  ): CloudKitQueryPageResult
}

/**
 * Single page of CloudKit query results.
 */
data class CloudKitQueryPage(
  val records: List<CKRecord>,
  val cursor: CKQueryCursor?,
)

/**
 * Swift-friendly wrapper for query results.
 */
sealed class CloudKitQueryPageResult {
  data class Ok(val value: CloudKitQueryPage) : CloudKitQueryPageResult()

  data class Err(val error: NSError) : CloudKitQueryPageResult()
}

/**
 * Converts the Swift-friendly wrapper into a Kotlin [Result].
 */
fun CloudKitQueryPageResult.toResult(): Result<CloudKitQueryPage, NSError> =
  when (this) {
    is CloudKitQueryPageResult.Ok -> ResultOk(value)
    is CloudKitQueryPageResult.Err -> ResultErr(error)
  }
