package build.wallet.cloud.store

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.data.MimeType
import okio.ByteString

/**
 * Android delegate implementation of a [CloudFileStore].
 *
 * Currently only Google Drive is supported.
 */

@BitkeyInject(AppScope::class)
class CloudFileStoreImpl(
  private val googleDriveFileStore: GoogleDriveFileStore,
) : CloudFileStore {
  override suspend fun exists(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<Boolean> {
    return when (account) {
      is GoogleAccount -> googleDriveFileStore.exists(account, fileName)
      else -> error("Cloud store account $account is not supported.")
    }
  }

  override suspend fun read(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<ByteString> {
    return when (account) {
      is GoogleAccount -> googleDriveFileStore.read(account, fileName)
      else -> error("Cloud store account $account is not supported.")
    }
  }

  override suspend fun remove(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<Unit> {
    return when (account) {
      is GoogleAccount -> googleDriveFileStore.remove(account, fileName)
      else -> error("Cloud store account $account is not supported.")
    }
  }

  override suspend fun write(
    account: CloudStoreAccount,
    bytes: ByteString,
    fileName: String,
    mimeType: MimeType,
  ): CloudFileStoreResult<Unit> {
    return when (account) {
      is GoogleAccount -> googleDriveFileStore.write(account, bytes, fileName, mimeType)
      else -> error("Cloud store account $account is not supported.")
    }
  }
}
