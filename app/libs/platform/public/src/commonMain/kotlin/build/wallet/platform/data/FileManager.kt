package build.wallet.platform.data

/**
 * An object that handles file I/O operations.
 * Allows iOS and Android to implement platform-specific implementations.
 */
interface FileManager {
  /**
   * Writes the given data to the given file.
   */
  suspend fun writeFile(
    data: ByteArray,
    fileName: String,
  ): FileManagerResult<Unit>

  /**
   * Deletes the given file.
   */
  suspend fun deleteFile(fileName: String): FileManagerResult<Unit>

  /**
   * Reads the given file as data.
   */
  suspend fun readFileAsBytes(fileName: String): FileManagerResult<ByteArray>

  /**
   * Reads the given file as a string.
   */
  suspend fun readFileAsString(fileName: String): FileManagerResult<String>

  /**
   * Unzips the given file to the target directory.
   */
  suspend fun unzipFile(
    zipPath: String,
    targetDirectory: String,
  ): FileManagerResult<Unit>

  /**
   * Check if a file exists.
   */
  suspend fun fileExists(fileName: String): Boolean

  /**
   * Remove a directory.
   */
  suspend fun removeDir(path: String): FileManagerResult<Unit>

  /**
   * Creates a directory for given path, including intermediate directories.
   */
  suspend fun mkdirs(path: String): FileManagerResult<Boolean>
}
