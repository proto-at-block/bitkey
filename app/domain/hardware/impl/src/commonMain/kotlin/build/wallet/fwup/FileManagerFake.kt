package build.wallet.fwup

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.platform.data.FileManager
import build.wallet.platform.data.FileManagerError
import build.wallet.platform.data.FileManagerResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Injectable fake implementation of [FileManager] for testing with fake hardware.
 *
 * Stores files in memory and provides the same interface as the real implementation.
 * This allows the firmware update flow to work end-to-end through the real manifest
 * parser while using fake data.
 */
@Fake
@BitkeyInject(AppScope::class)
class FileManagerFake : FileManager {
  private val lock = Mutex()
  private val files = mutableMapOf<String, ByteArray>()

  override suspend fun writeFile(
    data: ByteArray,
    fileName: String,
  ): FileManagerResult<Unit> =
    lock.withLock {
      files[fileName] = data
      FileManagerResult.Ok(Unit)
    }

  override suspend fun deleteFile(fileName: String): FileManagerResult<Unit> =
    lock.withLock {
      files.remove(fileName)
      FileManagerResult.Ok(Unit)
    }

  override suspend fun readFileAsBytes(fileName: String): FileManagerResult<ByteArray> =
    lock.withLock {
      files[fileName]?.let { FileManagerResult.Ok(it) }
        ?: FileManagerResult.Err(FileManagerError(Throwable("File not found: $fileName")))
    }

  override suspend fun readFileAsString(fileName: String): FileManagerResult<String> =
    lock.withLock {
      files[fileName]?.let { FileManagerResult.Ok(it.decodeToString()) }
        ?: FileManagerResult.Err(FileManagerError(Throwable("File not found: $fileName")))
    }

  override suspend fun unzipFile(
    zipPath: String,
    targetDirectory: String,
  ): FileManagerResult<Unit> {
    // No-op for fake - files are written directly by FirmwareDownloaderFake
    return FileManagerResult.Ok(Unit)
  }

  override suspend fun fileExists(fileName: String): Boolean =
    lock.withLock {
      files.containsKey(fileName)
    }

  override suspend fun removeDir(path: String): FileManagerResult<Unit> =
    lock.withLock {
      // Remove all files that start with the path prefix
      val keysToRemove = files.keys.filter { it.startsWith(path) }
      keysToRemove.forEach { files.remove(it) }
      FileManagerResult.Ok(Unit)
    }

  override suspend fun mkdirs(path: String): FileManagerResult<Boolean> {
    // No-op for in-memory storage
    return FileManagerResult.Ok(true)
  }

  /**
   * Resets all stored files for testing.
   */
  suspend fun reset(): Unit =
    lock.withLock {
      files.clear()
    }

  /**
   * Returns all stored file paths for debugging.
   */
  suspend fun getStoredFiles(): Set<String> =
    lock.withLock {
      files.keys.toSet()
    }
}
