package build.wallet.emergencyexitkit

import bitkey.account.AccountConfigServiceFake
import build.wallet.cloud.store.CloudAccountMock
import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudFileStoreDelegate
import build.wallet.cloud.store.CloudFileStoreFake
import build.wallet.cloud.store.CloudFileStoreFakeImpl
import build.wallet.platform.data.FileManager
import build.wallet.platform.data.FileManagerMock
import build.wallet.platform.data.MimeType
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
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

  test("fake cloud mode writes and reads through local fake file storage") {
    val accountConfigService = AccountConfigServiceFake()
    val realCloudFileStore = CloudFileStoreFakeImpl(FileManagerMock().apply { failWrite = true })
    val fakeCloudFileStore = CloudFileStoreFakeImpl(FileManagerMock())
    val repository = EmergencyExitKitRepositoryImpl(
      CloudFileStoreDelegate(
        realStore = realCloudFileStore,
        fakeStore = fakeCloudFileStore,
        accountConfigService = accountConfigService
      )
    )
    val staleRealAccount = CloudAccountMock("stale-real-account")
    accountConfigService.setIsCloudStoreFake(true)

    repository.write(staleRealAccount, emergencyExitKitData).shouldBeOk(Unit)
    repository.read(staleRealAccount)
      .map { it.pdfData }
      .value
      .shouldBeEqual(emergencyExitKitData.pdfData)
  }
})
