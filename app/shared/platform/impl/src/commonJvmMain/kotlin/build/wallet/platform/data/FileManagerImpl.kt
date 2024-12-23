package build.wallet.platform.data

import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.mapUnit
import build.wallet.platform.data.File.join
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import java.io.File
import java.util.zip.ZipFile

@BitkeyInject(AppScope::class)
class FileManagerImpl(
  private val fileDirectoryProvider: FileDirectoryProvider,
) : FileManager {
  override suspend fun writeFile(
    data: ByteArray,
    fileName: String,
  ): FileManagerResult<Unit> {
    return catchingResult {
      withContext(Dispatchers.IO) {
        val filePath = (fileDirectoryProvider.filesDir().join(fileName)).toPath()
        FileSystem.SYSTEM.write(filePath) {
          write(data)
        }
      }
    }
      .mapError { FileManagerError(it) }
      .logFailure { "Failed to write to file [$fileName]" }
      .mapUnit()
      .toFileManagerResult()
  }

  override suspend fun readFileAsString(fileName: String): FileManagerResult<String> {
    return catchingResult {
      withContext(Dispatchers.IO) {
        File(fileDirectoryProvider.filesDir().join(fileName)).readText()
      }
    }
      .mapError { FileManagerError(it) }
      .logFailure { "Failed to read file [$fileName] as string" }
      .toFileManagerResult()
  }

  override suspend fun readFileAsBytes(fileName: String): FileManagerResult<ByteArray> {
    return catchingResult {
      withContext(Dispatchers.IO) {
        File(fileDirectoryProvider.filesDir().join(fileName)).readBytes()
      }
    }
      .mapError { FileManagerError(it) }
      .logFailure { "Failed to read file [$fileName] as bytes" }
      .toFileManagerResult()
  }

  override suspend fun unzipFile(
    zipPath: String,
    targetDirectory: String,
  ): FileManagerResult<Unit> {
    return catchingResult {
      withContext(Dispatchers.IO) {
        unzip(zipPath, targetDirectory)
      }
    }
      .mapError { FileManagerError(it) }
      .logFailure { "Failed to unzip file [$zipPath] into target dir [$targetDirectory]" }
      .toFileManagerResult()
  }

  override suspend fun fileExists(fileName: String): Boolean {
    return File(fileDirectoryProvider.filesDir(), fileName).exists()
  }

  override suspend fun removeDir(path: String): FileManagerResult<Unit> {
    return catchingResult {
      withContext(Dispatchers.IO) {
        if (!File(fileDirectoryProvider.filesDir(), path).deleteRecursively()) {
          throw FileManagerError(Error("can't delete"))
        }
      }
    }
      .mapError { FileManagerError(it) }
      .logFailure { "Failed to delete $path" }
      .toFileManagerResult()
  }

  override suspend fun mkdirs(path: String): FileManagerResult<Boolean> {
    return catchingResult {
      withContext(Dispatchers.IO) {
        val filePath = (fileDirectoryProvider.filesDir().join(path)).toPath()
        File(path).mkdirs()
      }
    }
      .mapError { FileManagerError(it) }
      .logFailure { "Failed to make directories for dir [$path]" }
      .toFileManagerResult()
  }

  @Suppress("NestedBlockDepth")
  private fun unzip(
    zipPath: String,
    targetDirectory: String,
  ) {
    val zipFile = ZipFile(File(fileDirectoryProvider.filesDir(), zipPath).absolutePath)
    val targetFile = File(fileDirectoryProvider.filesDir(), targetDirectory)

    targetFile.mkdirs()

    zipFile.entries().asSequence().forEach { entry ->
      val newFile = File(targetFile, entry.name)

      val canonicalTargetDirPath = targetFile.canonicalPath
      val canonicalNewFilePath = newFile.canonicalPath

      /**
       * Validate the path of the new file before it's created.
       * Checks if the canonical path of the new file starts with the canonical path of the target
       * directory.
       * Prevents a Zip Slip vulnerability attach which is a form of directory traversal attack.
       * Without this validation, a zip entry could potentially write a file outside the target directory.
       */
      if (!canonicalNewFilePath.startsWith(canonicalTargetDirPath)) {
        throw SecurityException("Zip entry is outside of the target dir: ${entry.name}")
      }

      if (entry.isDirectory) {
        newFile.mkdirs()
      } else {
        zipFile.getInputStream(entry).use { input ->
          newFile.outputStream().use { output ->
            input.copyTo(output)
          }
        }
      }
    }
    zipFile.close()
  }
}
