package build.wallet.cloud.store

import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import platform.CloudKit.CKErrorDomain
import platform.CloudKit.CKErrorUnknownItem
import platform.CloudKit.CKRecord
import platform.CloudKit.CKRecordID
import platform.CloudKit.CKRecordValueProtocol
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create

class CloudKitKeyValueStoreImplTests : FunSpec({
  val account = iCloudAccount(ubiquityIdentityToken = "test-token")

  test("set stores value in CloudKit record") {
    val database = CloudKitDatabaseFake()
    val store = CloudKitKeyValueStoreImpl(database)

    val value = "test-value".encodeUtf8()
    store.set(account, key = "test-key", value = value).shouldBeOk()

    val record = database.records["test-key"]
    record shouldNotBe null
    val storedValue = record?.objectForKey("value") as? NSData
    storedValue shouldNotBe null
    storedValue?.toByteString() shouldBe value
  }

  test("set updates existing record when present") {
    val database = CloudKitDatabaseFake()
    val recordId = CKRecordID(recordName = "test-key")
    val record = CKRecord(recordType = "CloudKeyValue", recordID = recordId)
    record.setObject("old".encodeUtf8().toNSData() as CKRecordValueProtocol, forKey = "value")
    database.records["test-key"] = record

    val store = CloudKitKeyValueStoreImpl(database)
    store.set(account, key = "test-key", value = "new".encodeUtf8()).shouldBeOk()

    val updated = database.records["test-key"]
    val storedValue = updated?.objectForKey("value") as? NSData
    storedValue?.toByteString() shouldBe "new".encodeUtf8()
  }

  test("set returns CloudKit error on save failure") {
    val saveError = NSError.errorWithDomain("Test", 456, null)
    val database = CloudKitDatabaseFake(saveError = saveError)
    val store = CloudKitKeyValueStoreImpl(database)

    val error = store.set(account, key = "test-key", value = "value".encodeUtf8())
      .shouldBeErrOfType<CloudKitKeyValueStoreError>()

    error.rectificationData shouldBe saveError
  }

  test("get returns null on unknown item error") {
    val database = CloudKitDatabaseFake(fetchError = unknownItemError())
    val store = CloudKitKeyValueStoreImpl(database)

    store.get(account, key = "missing").shouldBeOk(null)
  }

  test("get returns CloudKit error on failure") {
    val database = CloudKitDatabaseFake(fetchError = NSError.errorWithDomain("Test", 123, null))
    val store = CloudKitKeyValueStoreImpl(database)

    store.get(account, key = "test-key").shouldBeErrOfType<CloudKitKeyValueStoreError>()
  }

  test("remove succeeds on unknown item error") {
    val database = CloudKitDatabaseFake(deleteError = unknownItemError())
    val store = CloudKitKeyValueStoreImpl(database)

    store.remove(account, key = "missing").shouldBeOk()
  }

  test("keys returns record names") {
    val database = CloudKitDatabaseFake()
    database.records["one"] = CKRecord(recordType = "CloudKeyValue", recordID = CKRecordID(recordName = "one"))
    database.records["two"] = CKRecord(recordType = "CloudKeyValue", recordID = CKRecordID(recordName = "two"))

    val store = CloudKitKeyValueStoreImpl(database)

    store.keys(account).shouldBeOk(setOf("one", "two"))
  }

  test("keys returns CloudKit error on query failure") {
    val queryError = NSError.errorWithDomain("Test", 789, null)
    val database = CloudKitDatabaseFake(queryError = queryError)
    val store = CloudKitKeyValueStoreImpl(database)

    val error = store.keys(account).shouldBeErrOfType<CloudKitKeyValueStoreError>()

    error.rectificationData shouldBe queryError
  }
})

private fun unknownItemError(): NSError =
  NSError.errorWithDomain(CKErrorDomain, CKErrorUnknownItem, null)

@OptIn(ExperimentalForeignApi::class)
private fun ByteString.toNSData(): NSData =
  memScoped {
    NSData.create(bytes = allocArrayOf(toByteArray()), length = size.toULong())
  }
