package build.wallet.cloud.store

import build.wallet.platform.data.MimeType
import okio.ByteString

class CloudFileStoreImpl : CloudFileStore {
  override suspend fun exists(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<Boolean> {
    TODO("Tracked by BKR-649")
  }

  override suspend fun read(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<ByteString> {
    TODO("Tracked by BKR-649")
  }

  override suspend fun remove(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<Unit> {
    TODO("Tracked by BKR-649")
  }

  override suspend fun write(
    account: CloudStoreAccount,
    bytes: ByteString,
    fileName: String,
    mimeType: MimeType,
  ): CloudFileStoreResult<Unit> {
    TODO("Tracked by BKR-649")
  }
}
