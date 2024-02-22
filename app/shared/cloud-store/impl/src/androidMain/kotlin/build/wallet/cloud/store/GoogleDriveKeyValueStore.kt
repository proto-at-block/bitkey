package build.wallet.cloud.store

import com.github.michaelbull.result.Result

/**
 * Key-value store abstraction around Google Drive APIs.
 *
 * Key is the file name in the root folder of the Google Drive
 * [app directory](https://developers.google.com/drive/api/guides/appdata).
 *
 * Value is the file content.
 */
interface GoogleDriveKeyValueStore {
  /**
   * Encodes [value] as UTF-8 string and writes it to a file with [key] name.
   */
  suspend fun setString(
    account: GoogleAccount,
    key: String,
    value: String,
  ): Result<Unit, GoogleDriveError>

  /**
   * Reads the file with [key] name and decodes it as UTF-8 string.
   */
  suspend fun getString(
    account: GoogleAccount,
    key: String,
  ): Result<String?, GoogleDriveError>

  /**
   * Remove the file with [key] name.
   */
  suspend fun remove(
    account: GoogleAccount,
    key: String,
  ): Result<Unit, GoogleDriveError>
}
