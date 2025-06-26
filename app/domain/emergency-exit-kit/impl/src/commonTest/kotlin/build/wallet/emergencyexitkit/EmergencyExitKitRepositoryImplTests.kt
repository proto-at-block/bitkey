package build.wallet.emergencyexitkit

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

class EmergencyExitKitRepositoryImplTests : FunSpec({
  lateinit var fileManager: FileManager
  lateinit var cloudFileStore: CloudFileStoreFake
  lateinit var repository: EmergencyExitKitRepository

  val dummyPdfData = "dummy-pdf-data".encodeToByteArray().toByteString()
  val emergencyExitKitData = EmergencyExitKitData(dummyPdfData)

  val originalFileName = "Emergency Access Kit.pdf"
  val newFileName = "Emergency Exit Kit.pdf"

  beforeTest {
    fileManager = FileManagerMock()
    cloudFileStore = CloudFileStoreFake(
      parentDir = "foo/files",
      fileManager = fileManager
    )
    repository = EmergencyExitKitRepositoryImpl(cloudFileStore)
  }

  test("read new filename successfully") {
    val account = CloudAccountMock("foo")
    cloudFileStore.write(account, emergencyExitKitData.pdfData, newFileName, MimeType.PDF)
    repository.read(account)
      .map { it.pdfData }
      .value
      .shouldBeEqual(emergencyExitKitData.pdfData)
  }

  test("read original filename and error") {
    val account = CloudAccountMock("foo")
    cloudFileStore.write(account, emergencyExitKitData.pdfData, originalFileName, MimeType.PDF)
    repository.read(account).shouldBeErrOfType<EmergencyExitKitRepositoryError.RectifiableCloudError>()
  }

  test("write new filename successfully when old file does not exist") {
    val account = CloudAccountMock("foo")
    repository.write(account, emergencyExitKitData)
    cloudFileStore.read(account, "Emergency Exit Kit.pdf").result.value
      .shouldBeEqual(emergencyExitKitData.pdfData)
  }

  test("write new filename successfully and delete old file when old file exists") {
    val account = CloudAccountMock("foo")
    val oldEEK = EmergencyExitKitData("old pdf data".encodeToByteArray().toByteString())
    cloudFileStore.write(account, oldEEK.pdfData, originalFileName, MimeType.PDF)
    repository.write(account, emergencyExitKitData)
    cloudFileStore.read(account, "Emergency Exit Kit.pdf").result.value
      .shouldBeEqual(emergencyExitKitData.pdfData)
    cloudFileStore.read(account, "Emergency Access Kit.pdf").result.shouldBeErrOfType<CloudError>()
  }

  test("don't delete old file if we fail to write the new file") {
    val account = CloudAccountMock("foo")
    val oldEEK = EmergencyExitKitData("old pdf data".encodeToByteArray().toByteString())
    cloudFileStore.write(account, oldEEK.pdfData, originalFileName, MimeType.PDF)
    (fileManager as? FileManagerMock)?.failWrite = true
    repository.write(account, emergencyExitKitData)
    cloudFileStore.read(account, "Emergency Access Kit.pdf").result.value
      .shouldBeEqual(oldEEK.pdfData)
    cloudFileStore.read(account, "Emergency Exit Kit.pdf").result.shouldBeErrOfType<CloudError>()
  }
})
