package build.wallet.cloud.store

import android.accounts.Account
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.LogLevel
import build.wallet.logging.logFailure
import build.wallet.mapUnit
import build.wallet.platform.data.MimeType
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

@BitkeyInject(AppScope::class)
class GoogleDriveKeyValueStoreImpl(
  private val googleDriveClientProvider: GoogleDriveClientProvider,
) : GoogleDriveKeyValueStore {
  /**
   * Lock to ensure that ensure concurrent-safe access to the Google Drive API.
   * Prevents race conditions when multiple coroutines try to read/write to Google Drive at the
   * same time.
   *
   * Prevents race conditions issues like W-6528 where multiple folders were created in Google Drive
   * for the same account.
   */
  private val driveClientLock = Mutex()

  private suspend fun drive(androidAccount: Account): Result<Drive, GoogleDriveError> {
    return googleDriveClientProvider.drive(androidAccount, scope = GoogleDriveScope.AppData)
  }

  override suspend fun setString(
    account: GoogleAccount,
    key: String,
    value: String,
  ): Result<Unit, GoogleDriveError> =
    coroutineBinding {
      driveClientLock.withLock {
        val androidAccount = account.credentials.androidAccount().bind()
        val drive = drive(androidAccount).bind()

        val entryFile = drive.findEntryFile(key).bind()
        if (entryFile == null) {
          drive.createNewEntryFile(key, value.encodeUtf8()).bind()
        } else {
          drive.updateExistingEntryFile(entryFile.id, key, value.encodeUtf8()).bind()
        }
      }
    }.logFailure { "Error setting value for key=$key on Google Drive" }

  override suspend fun remove(
    account: GoogleAccount,
    key: String,
  ): Result<Unit, GoogleDriveError> =
    coroutineBinding {
      driveClientLock.withLock {
        val androidAccount = account.credentials.androidAccount().bind()
        val drive = drive(androidAccount).bind()

        val entryFile =
          drive.findEntryFile(key)
            .flatMap { entryFile ->
              when (entryFile) {
                null -> Err(GoogleDriveError(message = "Failed to delete key=$key; file does not exist"))
                else -> Ok(entryFile)
              }
            }
            .bind()

        drive.deleteExistingEntryFile(entryFile.id).bind()
      }
    }.logFailure(LogLevel.Warn) { "Error deleting value for key=$key from Google Drive" }

  override suspend fun keys(account: GoogleAccount): Result<List<String>, GoogleDriveError> =
    coroutineBinding {
      driveClientLock.withLock {
        val androidAccount = account.credentials.androidAccount().bind()
        val drive = drive(androidAccount).bind()
        runInterruptible(Dispatchers.IO) {
          drive.files()
            .list()
            .setSpaces(APP_DATA_FOLDER)
            .execute()
            .files
            .map { it.name }
        }
      }
    }

  override suspend fun getString(
    account: GoogleAccount,
    key: String,
  ): Result<String?, GoogleDriveError> =
    coroutineBinding {
      driveClientLock.withLock {
        val androidAccount = account.credentials.androidAccount().bind()
        val drive = drive(androidAccount).bind()

        val file = drive.findEntryFile(key).bind()

        file?.let {
          val fileData = drive.downloadEntryFile(file).bind()
          fileData.utf8()
        }
      }
    }.logFailure { "Error reading string value for key=$key from Google Drive cloud storage" }

  private suspend fun Drive.findEntryFile(key: String): Result<File?, GoogleDriveError> =
    catchingResult {
      val files =
        runInterruptible(Dispatchers.IO) {
          files()
            .list()
            .setSpaces(APP_DATA_FOLDER)
            .setQ("name='$key'")
            .execute()
        }

      files.files.find { it.name == key }
    }
      .mapError { throwable ->
        when (throwable) {
          is UserRecoverableAuthIOException -> {
            GoogleDriveError(
              cause = throwable,
              rectificationData = throwable.intent
            )
          }
          else -> {
            GoogleDriveError(cause = throwable)
          }
        }
      }

  /**
   * Create new Drive File which acts as a key/value entry for the store.
   * Entry's [key] is used as file's name.
   */
  private suspend fun Drive.createNewEntryFile(
    key: String,
    value: ByteString,
  ): Result<Unit, GoogleDriveError> =
    catchingResult {
      val fileMetadata =
        File()
          .setName(key)
          .setParents(listOf(APP_DATA_FOLDER))
          .setMimeType(MimeType.TEXT_PLAIN.name)

      val fileContent = ByteArrayContent(null, value.toByteArray())
      runInterruptible(Dispatchers.IO) {
        files().create(fileMetadata, fileContent).execute()
      }
    }
      .mapUnit()
      .mapError { GoogleDriveError(cause = it) }

  private suspend fun Drive.deleteExistingEntryFile(
    existingEntryFileId: String,
  ): Result<Unit, GoogleDriveError> =
    catchingResult {
      runInterruptible(Dispatchers.IO) {
        files().delete(existingEntryFileId).execute()
      }
    }
      .mapUnit()
      .mapError { GoogleDriveError(cause = it) }

  /**
   * Update existing entry for given entry [key] and entry Drive [File].
   */
  private suspend fun Drive.updateExistingEntryFile(
    existingEntryFileId: String,
    key: String,
    value: ByteString,
  ): Result<Unit, GoogleDriveError> =
    catchingResult {
      val fileMetadata = File().setName(key)
      val fileContent = ByteArrayContent(null, value.toByteArray())
      runInterruptible(Dispatchers.IO) {
        files().update(existingEntryFileId, fileMetadata, fileContent).execute()
      }
    }
      .mapUnit()
      .mapError { GoogleDriveError(cause = it) }

  /**
   * Download [File] entry's value contents.
   */
  private suspend fun Drive.downloadEntryFile(file: File): Result<ByteString, GoogleDriveError> =
    catchingResult {
      val buffer = Buffer()
      val get = files().get(file.id)
      buffer.use {
        runInterruptible(Dispatchers.IO) {
          get.executeMediaAndDownloadTo(buffer.outputStream())
        }
      }
      buffer.readByteString()
    }
      .mapError { GoogleDriveError(cause = it) }

  private companion object {
    /**
     * As per https://developers.google.com/drive/api/guides/appdata.
     */
    const val APP_DATA_FOLDER = "appDataFolder"
  }
}
