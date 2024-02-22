package build.wallet.cloud.store

import android.accounts.Account
import build.wallet.catching
import build.wallet.platform.PlatformContext
import build.wallet.platform.config.AppId
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes

interface GoogleDriveService {
  /**
   * Returns Google Drive API service definition for managing [androidAccount]'s files. Scoped to:
   *
   * 1. application-specific data (a private folder in Google Drive not visible to customer). See
   *    <https://developers.google.com/drive/api/guides/appdata> for more.
   * 2. files created by this app ID (in the Google Drive visible to customer), using the
   *    `drive.file` scope at <https://developers.google.com/drive/api/guides/api-specific-auth>.
   */
  fun drive(androidAccount: Account): Result<Drive, GoogleDriveError>
}

class GoogleDriveServiceImpl(
  private val appId: AppId,
  private val platformContext: PlatformContext,
) : GoogleDriveService {
  private val scopes =
    listOf(
      DriveScopes.DRIVE_APPDATA, // used by [GoogleDriveKeyValueStore] for storing encrypted app backup
      DriveScopes.DRIVE_FILE // used by [GoogleDriveFileStore] for storing Emergency Access Kit PDF
    )

  override fun drive(androidAccount: Account): Result<Drive, GoogleDriveError> =
    Result
      .catching {
        val credential =
          GoogleAccountCredential
            .usingOAuth2(platformContext.appContext, scopes)
            .also {
              it.selectedAccount = androidAccount
            }
        val httpTransport = NetHttpTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        Drive.Builder(httpTransport, jsonFactory, credential)
          .setApplicationName(appId.value)
          .build()
      }
      .mapError { GoogleDriveError(cause = it) }
}
