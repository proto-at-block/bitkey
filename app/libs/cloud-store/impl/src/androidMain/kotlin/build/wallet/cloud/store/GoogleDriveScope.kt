package build.wallet.cloud.store

import com.google.api.services.drive.DriveScopes

/**
 * Google Drive API scopes.
 *
 * See [Choose Google Drive API scopes](https://developers.google.com/drive/api/guides/api-specific-auth)
 */
enum class GoogleDriveScope(val uri: String) {
  /**
   * Application-specific data (a private folder in Google Drive not visible to customer).
   *
   *  See [Store application-specific data](https://developers.google.com/drive/api/guides/appdata).
   */
  AppData(DriveScopes.DRIVE_APPDATA),

  /**
   * Files created by this app ID (in the Google Drive visible to customer).
   *
   * See [Benefits of the drive.file OAuth scope](https://developers.google.com/drive/api/guides/api-specific-auth#benefits)
   */
  File(DriveScopes.DRIVE_FILE),
}
