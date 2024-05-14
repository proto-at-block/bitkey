package build.wallet.cloud.store

import android.accounts.Account
import build.wallet.catching
import build.wallet.logging.logFailure
import build.wallet.mapUnit
import build.wallet.platform.data.MimeType
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.recover
import com.github.michaelbull.result.toErrorIfNull
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Buffer
import okio.ByteString

class GoogleDriveFileStoreImpl(
  private val googleDriveService: GoogleDriveService,
) : GoogleDriveFileStore {
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
    return googleDriveService.drive(androidAccount, scope = GoogleDriveScope.File)
  }

  override suspend fun exists(
    account: GoogleAccount,
    fileName: String,
  ): CloudFileStoreResult<Boolean> =
    binding {
      driveClientLock.withLock {
        val androidAccount = account.credentials.androidAccount().bind()
        val driveService = drive(androidAccount).bind()

        val bitkeyFolderId = driveService.getBitkeyFolderId().bind()

        bitkeyFolderId?.let {
          driveService.getFileIdInFolder(bitkeyFolderId, fileName).bind()
        }
      }
    }
      .map { it != null }
      .logFailure { "Failed to check existence of file='$fileName' in Bitkey folder on Google Drive" }
      .toCloudFileStoreResult()

  override suspend fun read(
    account: GoogleAccount,
    fileName: String,
  ): CloudFileStoreResult<ByteString> =
    binding {
      driveClientLock.withLock {
        val androidAccount = account.credentials.androidAccount().bind()
        val driveService = drive(androidAccount).bind()

        val fileId = getFileIdInBitkeyFolder(account, fileName).bind()

        driveService.downloadFile(fileId).bind()
      }
    }
      .logFailure { "Failed to read file='$fileName' in Bitkey folder on Google Drive" }
      .toCloudFileStoreResult()

  override suspend fun remove(
    account: GoogleAccount,
    fileName: String,
  ): CloudFileStoreResult<Unit> =
    binding {
      driveClientLock.withLock {
        val androidAccount = account.credentials.androidAccount().bind()
        val driveService = drive(androidAccount).bind()

        val fileId = getFileIdInBitkeyFolder(account, fileName).bind()

        driveService.deleteFile(fileId).bind()
      }
    }
      .logFailure { "Failed to delete file='$fileName' in Bitkey folder on Google Drive" }
      .toCloudFileStoreResult()

  override suspend fun write(
    account: GoogleAccount,
    bytes: ByteString,
    fileName: String,
    mimeType: MimeType,
  ): CloudFileStoreResult<Unit> =
    binding {
      driveClientLock.withLock {
        val androidAccount = account.credentials.androidAccount().bind()
        val driveService = drive(androidAccount).bind()

        val bitkeyFolderId =
          driveService.getBitkeyFolderId()
            .recover { null } // continue from the error when the Bitkey folder doesn't exist yet
            .bind()

        if (bitkeyFolderId == null) {
          // The Bitkey folder doesn't yet exist; so create it and then create the file.
          val newBitkeyFolderId = driveService.createBitkeyFolder().bind()
          driveService.createFile(fileName, mimeType, newBitkeyFolderId, bytes).bind()
        } else {
          // The Bitkey folder does exist; so check to see if the file already exists to decide if the
          // file should be updated or created.
          val fileId = driveService.getFileIdInFolder(bitkeyFolderId, fileName).bind()
          if (fileId == null) {
            driveService.createFile(fileName, mimeType, bitkeyFolderId, bytes).bind()
          } else {
            driveService.updateFile(fileId, mimeType, bytes).bind()
          }
        }
      }
    }
      .logFailure { "Failed to write file='$fileName' in Bitkey folder on Google Drive" }
      .toCloudFileStoreResult()

  private suspend fun Drive.createFile(
    fileName: String,
    mimeType: MimeType,
    bitkeyFolderId: String,
    bytes: ByteString,
  ): Result<Unit, GoogleDriveError> =
    Result
      .catching {
        val fileMetadata =
          File()
            .setName(fileName)
            .setMimeType(mimeType.name)
            .setParents(listOf(bitkeyFolderId))

        val fileContent = ByteArrayContent(mimeType.name, bytes.toByteArray())
        runInterruptible(Dispatchers.IO) {
          files().create(fileMetadata, fileContent).execute()
        }
      }
      .mapUnit()
      .mapError { GoogleDriveError(cause = it) }

  private suspend fun Drive.createBitkeyFolder(): Result<String, GoogleDriveError> =
    Result
      .catching {
        val fileMetadata =
          File()
            .setName(BITKEY_FOLDER)
            .setMimeType(MimeType.GOOGLE_DRIVE_FOLDER.name)

        runInterruptible(Dispatchers.IO) {
          files().create(fileMetadata)
            .setFields("id")
            .execute()
        }
      }
      .map { it.id }
      .mapError { GoogleDriveError(cause = it) }

  private suspend fun Drive.deleteFile(fileId: String): Result<Unit, GoogleDriveError> =
    Result
      .catching {
        runInterruptible(Dispatchers.IO) {
          files().delete(fileId).execute()
        }
      }
      .mapUnit()
      .mapError { GoogleDriveError(cause = it) }

  private suspend fun Drive.downloadFile(fileId: String): Result<ByteString, GoogleDriveError> =
    Result
      .catching {
        val buffer = Buffer()
        val get = files().get(fileId)
        buffer.use {
          runInterruptible(Dispatchers.IO) {
            get.executeMediaAndDownloadTo(buffer.outputStream())
          }
        }
        buffer.readByteString()
      }
      .mapError { GoogleDriveError(cause = it) }

  private suspend fun Drive.getBitkeyFolderId(): Result<String?, GoogleDriveError> =
    Result
      .catching {
        runInterruptible(Dispatchers.IO) {
          files().list()
            .setQ(
              "mimeType = '${MimeType.GOOGLE_DRIVE_FOLDER.name}' and name = '$BITKEY_FOLDER' and 'root' in parents"
            )
            .setSpaces(DRIVE_SPACE)
            .setFields("files(id)")
            .execute()
        }
      }
      .map { it.files }
      .map { it.getOrNull(0)?.id }
      .mapError { GoogleDriveError(cause = it) }

  private suspend fun Drive.getFileIdInFolder(
    folderId: String,
    fileName: String,
  ): Result<String?, GoogleDriveError> =
    Result
      .catching {
        runInterruptible(Dispatchers.IO) {
          files().list()
            .setQ("name = '$fileName' and '$folderId' in parents and trashed = false")
            .setSpaces(DRIVE_SPACE)
            .setFields("files(id)")
            .execute()
        }
      }
      .map { it.files }
      .map { it.getOrNull(0)?.id }
      .mapError { GoogleDriveError(cause = it) }

  private suspend fun Drive.updateFile(
    fileId: String,
    mimeType: MimeType,
    bytes: ByteString,
  ): Result<Unit, GoogleDriveError> =
    Result
      .catching {
        val fileContent = ByteArrayContent(mimeType.name, bytes.toByteArray())
        runInterruptible(Dispatchers.IO) {
          files().update(fileId, null, fileContent).execute()
        }
      }
      .mapUnit()
      .mapError { GoogleDriveError(cause = it) }

  private companion object {
    const val BITKEY_FOLDER = "Bitkey"
    const val DRIVE_SPACE = "drive"
  }

  private suspend fun getFileIdInBitkeyFolder(
    account: GoogleAccount,
    fileName: String,
  ): Result<String, GoogleDriveError> =
    binding {
      val androidAccount = account.credentials.androidAccount().bind()
      val driveService = drive(androidAccount).bind()

      val bitkeyFolderId =
        driveService.getBitkeyFolderId()
          .toErrorIfNull {
            GoogleDriveError(
              message = "Failed to find Bitkey folder in Google Drive"
            )
          }
          .bind()

      driveService.getFileIdInFolder(bitkeyFolderId, fileName).bind()
    }
      .toErrorIfNull {
        GoogleDriveError(message = "Failed to find fileId for fileName='$fileName' in Google Drive")
      }
}
