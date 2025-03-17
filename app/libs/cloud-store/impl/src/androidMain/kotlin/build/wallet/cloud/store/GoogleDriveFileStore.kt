package build.wallet.cloud.store

import build.wallet.platform.data.MimeType
import okio.ByteString

/**
 * File store abstraction around Google Drive APIs.
 *
 * All files accessed via this interface will:
 * 1. Reside in the 'Bitkey' folder in the root of the Google Drive (which will be created if needed
 *    when files are written).
 * 2. Overwrite existing files when a previously existing file is written to.
 */
interface GoogleDriveFileStore {
  suspend fun exists(
    account: GoogleAccount,
    fileName: String,
  ): CloudFileStoreResult<Boolean>

  suspend fun read(
    account: GoogleAccount,
    fileName: String,
  ): CloudFileStoreResult<ByteString>

  suspend fun remove(
    account: GoogleAccount,
    fileName: String,
  ): CloudFileStoreResult<Unit>

  suspend fun write(
    account: GoogleAccount,
    bytes: ByteString,
    fileName: String,
    mimeType: MimeType,
  ): CloudFileStoreResult<Unit>
}
