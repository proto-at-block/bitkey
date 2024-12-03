package build.wallet.platform.data

import build.wallet.platform.PlatformContext
import build.wallet.testing.shouldBeErrOfType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileManagerImplTests : FunSpec({
  val fileDirectoryProvider = FileDirectoryProviderImpl(PlatformContext())
  val fileManager = FileManagerImpl(fileDirectoryProvider)

  test("zip slip attack is prevented") {
    // Create a temporary directory for the test
    val tempDir = File(fileDirectoryProvider.filesDir(), "test").apply { mkdirs() }

    // Create a zip file with a directory traversal filename
    val zipFile = File(fileDirectoryProvider.filesDir(), "test.zip")
    ZipOutputStream(zipFile.outputStream()).use { zos ->
      zos.putNextEntry(ZipEntry("../../evil.sh"))
      zos.write("This is a test".toByteArray())
      zos.closeEntry()
    }

    fileManager
      .unzipFile(zipFile.name, tempDir.name)
      .result
      .shouldBeErrOfType<FileManagerError>()
      .cause
      .shouldBe(SecurityException("Zip entry is outside of the target dir: ../../evil.sh"))
  }
})
