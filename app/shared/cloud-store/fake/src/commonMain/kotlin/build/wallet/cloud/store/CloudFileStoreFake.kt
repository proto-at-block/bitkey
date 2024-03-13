package build.wallet.cloud.store

import build.wallet.platform.data.File.join
import build.wallet.platform.data.FileManager
import build.wallet.platform.data.FileManagerResult
import build.wallet.platform.data.MimeType
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * Fake implementation of [CloudFileStore] that manages files using a [FileManager].
 *
 * @param rootDir directory to create and manage all files under. Allows to reset this fake file
 *        store by deleting all directories and files inside this directory.
 */
class CloudFileStoreFake(
  private val parentDir: String,
  private val rootDir: String = "cloud-file-store-fake",
  private val fileManager: FileManager,
) : CloudFileStore {
  override suspend fun exists(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<Boolean> {
    return CloudFileStoreResult.Ok(fileManager.fileExists(rootDir.join(account.toString()).join(fileName)))
  }

  override suspend fun read(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<ByteString> {
    val accountDir = rootDir.join(account.toString()).join(fileName)
    return fileManager
      .readFileAsBytes(accountDir).result
      .map { it.toByteString() }
      .mapError { CloudError(it) }
      .toCloudFileStoreResult()
  }

  override suspend fun remove(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<Unit> {
    TODO("Not implemented")
  }

  override suspend fun write(
    account: CloudStoreAccount,
    bytes: ByteString,
    fileName: String,
    mimeType: MimeType,
  ): CloudFileStoreResult<Unit> {
    val accountDir = rootDir.join(account.toString())

    val absoluteAccountDir = parentDir.join(accountDir)
    fileManager.mkdirs(absoluteAccountDir)

    return fileManager
      .writeFile(
        data = bytes.toByteArray(),
        fileName = accountDir.join(fileName)
      )
      .result
      .mapError { CloudError(it) }
      .toCloudFileStoreResult()
  }

  suspend fun clear(): FileManagerResult<Unit> = fileManager.removeDir(rootDir)
}
