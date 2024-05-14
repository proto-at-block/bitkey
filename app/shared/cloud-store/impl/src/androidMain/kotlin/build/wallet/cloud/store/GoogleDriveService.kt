package build.wallet.cloud.store

import android.accounts.Account
import build.wallet.catching
import build.wallet.platform.PlatformContext
import build.wallet.platform.config.AppId
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onSuccess
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface GoogleDriveService {
  /**
   * Returns Google Drive API service definition for managing [androidAccount]'s files for given
   * GoogleDrive [scope].
   *
   * @param androidAccount Android account to use for authentication. The Android account instance comes from
   * [GoogleAccount] with which the customer logged into through the Google sign-in flow in the app UI.
   * @param scope Google Drive scope to use for the Google Drive client. Determines the permissions for the client.
   */
  suspend fun drive(
    androidAccount: Account,
    scope: GoogleDriveScope,
  ): Result<Drive, GoogleDriveError>
}

class GoogleDriveServiceImpl(
  private val appId: AppId,
  private val platformContext: PlatformContext,
) : GoogleDriveService {
  /** Cache of Google Drive API clients for each unique set of credentials. **/
  private val drives = mutableMapOf<DriveCredentials, Drive>()

  /**
   * Lock to ensure concurrent-safe access to the [drives] cache.
   *
   * Use to ensure that we use single Drive instance per given set of credentials.
   *
   * It's not very clear based on official Google Drive API documentation whether using multiple
   * Drive instances for the same account is thread-safe, but we are using this lock to prevent potential
   * race conditions, like W-6528 where multiple folders were created in Google Drive for the same account.
   **/
  private val drivesMapLock = Mutex()

  override suspend fun drive(
    androidAccount: Account,
    scope: GoogleDriveScope,
  ): Result<Drive, GoogleDriveError> {
    return drivesMapLock.withLock {
      // Look up the existing Drive instance for the given credentials.
      val credentials = DriveCredentials(androidAccount, scope)
      val existingDrive = drives[credentials]
      when {
        // Return the existing Drive instance if it exists.
        existingDrive != null -> Ok(existingDrive)
        else ->
          // Create and cache a new Drive instance if it doesn't exist.
          createDrive(androidAccount, scope)
            .onSuccess { drive ->
              drives[credentials] = drive
            }
      }
    }
  }

  /**
   * Creates a new Google Drive API client instance for the given [androidAccount] and [scope].
   */
  private fun createDrive(
    androidAccount: Account,
    scope: GoogleDriveScope,
  ): Result<Drive, GoogleDriveError> =
    Result
      .catching {
        val credential =
          GoogleAccountCredential
            .usingOAuth2(platformContext.appContext, setOf(scope.uri))
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

/**
 * Represents unique credentials for a Google Drive API client.
 * Primarily used as a composite key in the [GoogleDriveServiceImpl.drives] cache.
 *
 * Note that this intentionally only supports single scope per client. This is because
 * supporting multiple scopes would require a more complex cache key (we wouldn't want to
 * use different client instances for the same account but with overlapping scopes). Since we don't
 * have a use case for multiple scopes at the moment, we're keeping it simple.
 */
private data class DriveCredentials(
  val account: Account,
  val scope: GoogleDriveScope,
)
