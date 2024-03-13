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

interface GoogleDriveService {
  /**
   * Returns Google Drive API service definition for managing [androidAccount]'s files for a given
   * set of [GoogleDriveScope]s.
   */
  fun drive(
    androidAccount: Account,
    scopes: Collection<GoogleDriveScope>,
  ): Result<Drive, GoogleDriveError>
}

class GoogleDriveServiceImpl(
  private val appId: AppId,
  private val platformContext: PlatformContext,
) : GoogleDriveService {
  override fun drive(
    androidAccount: Account,
    scopes: Collection<GoogleDriveScope>,
  ): Result<Drive, GoogleDriveError> =
    Result
      .catching {
        val credential =
          GoogleAccountCredential
            .usingOAuth2(platformContext.appContext, scopes.map { it.uri })
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
