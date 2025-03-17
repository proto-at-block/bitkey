package build.wallet.platform.data

import build.wallet.platform.data.File.join

interface FileDirectoryProvider {
  /**
   * Returns the absolute path of the directory holding application data.
   * Does not contain trailing `/` file separator.
   */
  fun appDir(): String
}

/**
 * Returns the absolute path of the "databases/" subdirectory of the root app directory.
 */
fun FileDirectoryProvider.databasesDir(): String = appDir().join("databases")

/**
 * Returns the absolute path of the "files/" subdirectory of the root app directory.
 *
 * Currently [writeFile], [readFileAsBytes], [readFileAsString], and [unzipFile] all use this
 * directory.
 *
 * TODO(W-1782): update to use absolute files - need KMP representation of a `File`.
 */
fun FileDirectoryProvider.filesDir(): String = appDir().join("files")
