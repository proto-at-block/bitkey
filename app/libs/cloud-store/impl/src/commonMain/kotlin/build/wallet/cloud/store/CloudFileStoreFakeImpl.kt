package build.wallet.cloud.store

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.di.Impl
import build.wallet.platform.data.FileManager
import build.wallet.platform.data.FileManagerResult
import build.wallet.platform.data.MimeType
import okio.ByteString
import okio.ByteString.Companion.toByteString

@Fake
@BitkeyInject(AppScope::class)
class CloudFileStoreFakeImpl(
  @Impl private val fileManager: FileManager,
) : CloudFileStore {
  override suspend fun exists(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<Boolean> {
    if (account !is CloudStoreAccountFake) {
      return nonFakeAccountError()
    }
    return CloudFileStoreResult.Ok(fileManager.fileExists(filePath(account, fileName)))
  }

  override suspend fun read(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<ByteString> {
    if (account !is CloudStoreAccountFake) {
      return nonFakeAccountError()
    }
    return when (val result = fileManager.readFileAsBytes(filePath(account, fileName))) {
      is FileManagerResult.Ok -> CloudFileStoreResult.Ok(result.value.toByteString())
      is FileManagerResult.Err -> CloudFileStoreResult.Err(CloudError(result.error))
    }
  }

  override suspend fun remove(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<Unit> {
    if (account !is CloudStoreAccountFake) {
      return nonFakeAccountError()
    }
    return when (val result = fileManager.deleteFile(filePath(account, fileName))) {
      is FileManagerResult.Ok -> CloudFileStoreResult.Ok(Unit)
      is FileManagerResult.Err -> CloudFileStoreResult.Err(CloudError(result.error))
    }
  }

  override suspend fun write(
    account: CloudStoreAccount,
    bytes: ByteString,
    fileName: String,
    mimeType: MimeType,
  ): CloudFileStoreResult<Unit> {
    if (account !is CloudStoreAccountFake) {
      return nonFakeAccountError()
    }
    return when (val result = fileManager.writeFile(bytes.toByteArray(), filePath(account, fileName))) {
      is FileManagerResult.Ok -> CloudFileStoreResult.Ok(Unit)
      is FileManagerResult.Err -> CloudFileStoreResult.Err(CloudError(result.error))
    }
  }

  private fun filePath(
    account: CloudStoreAccountFake,
    fileName: String,
  ): String =
    listOf(
      FILE_PREFIX,
      account.identifier.sanitizedPathComponent(),
      fileName.sanitizedPathComponent()
    ).joinToString(separator = "_")

  private fun String.sanitizedPathComponent(): String {
    val sanitized = replace(Regex("""[^A-Za-z0-9._-]"""), "_")
    return sanitized.ifEmpty { "_" }
  }

  private fun <T : Any> nonFakeAccountError(): CloudFileStoreResult<T> =
    CloudFileStoreResult.Err(CloudError("Expected CloudStoreAccountFake"))

  private companion object {
    const val FILE_PREFIX = "cloud-file-store-fake"
  }
}
