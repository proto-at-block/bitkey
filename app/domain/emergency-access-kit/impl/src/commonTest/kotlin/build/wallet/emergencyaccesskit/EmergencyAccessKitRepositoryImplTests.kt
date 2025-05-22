package build.wallet.emergencyaccesskit

import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudFileStoreFake
import build.wallet.platform.data.FileManager
import build.wallet.platform.data.FileManagerMock
import build.wallet.platform.data.MimeType
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.map
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import okio.ByteString.Companion.toByteString

class EmergencyAccessKitRepositoryImplTests : FunSpec({
  lateinit var fileManager: FileManager
  lateinit var cloudFileStore: CloudFileStoreFake
  lateinit var repository: EmergencyAccessKitRepository

  val dummyPdfData = "dummy-pdf-data".encodeToByteArray().toByteString()
  val emergencyAccessKitData = EmergencyAccessKitData(dummyPdfData)

  val originalFileName = "Emergency Access Kit.pdf"
  val newFileName = "Emergency Exit Kit.pdf"

  beforeTest {
    fileManager = FileManagerMock()
    cloudFileStore = CloudFileStoreFake(
      parentDir = "foo/files",
      fileManager = fileManager
    )
    repository = EmergencyAccessKitRepositoryImpl(cloudFileStore)
  }

  test("read new filename successfully") {
    val account = CloudAccountMock("foo")
    cloudFileStore.write(account, emergencyAccessKitData.pdfData, newFileName, MimeType.PDF)
    repository.read(account)
      .map { it.pdfData }
      .value
      .shouldBeEqual(emergencyAccessKitData.pdfData)
  }

  test("read original filename and error") {
    val account = CloudAccountMock("foo")
    cloudFileStore.write(account, emergencyAccessKitData.pdfData, originalFileName, MimeType.PDF)
    repository.read(account).shouldBeErrOfType<EmergencyAccessKitRepositoryError.RectifiableCloudError>()
  }

  test("write new filename successfully when old file does not exist") {
    val account = CloudAccountMock("foo")
    repository.write(account, emergencyAccessKitData)
    cloudFileStore.read(account, "Emergency Exit Kit.pdf").result.value
      .shouldBeEqual(emergencyAccessKitData.pdfData)
  }

  test("write new filename successfully and delete old file when old file exists") {
    val account = CloudAccountMock("foo")
    val oldEAK = EmergencyAccessKitData("old pdf data".encodeToByteArray().toByteString())
    cloudFileStore.write(account, oldEAK.pdfData, originalFileName, MimeType.PDF)
    repository.write(account, emergencyAccessKitData)
    cloudFileStore.read(account, "Emergency Exit Kit.pdf").result.value
      .shouldBeEqual(emergencyAccessKitData.pdfData)
    cloudFileStore.read(account, "Emergency Access Kit.pdf").result.shouldBeErrOfType<CloudError>()
  }

  test("don't delete old file if we fail to write the new file") {
    val account = CloudAccountMock("foo")
    val oldEAK = EmergencyAccessKitData("old pdf data".encodeToByteArray().toByteString())
    cloudFileStore.write(account, oldEAK.pdfData, originalFileName, MimeType.PDF)
    (fileManager as? FileManagerMock)?.failWrite = true
    repository.write(account, emergencyAccessKitData)
    cloudFileStore.read(account, "Emergency Access Kit.pdf").result.value
      .shouldBeEqual(oldEAK.pdfData)
    cloudFileStore.read(account, "Emergency Exit Kit.pdf").result.shouldBeErrOfType<CloudError>()
  }
})
