package build.wallet.google.signin

import build.wallet.platform.PlatformContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope

class GoogleSignInClientProviderImpl(
  private val platformContext: PlatformContext,
) : GoogleSignInClientProvider {
  override val clientForGoogleDrive: GoogleSignInClient by lazy {
    val options =
      GoogleSignInOptions.Builder()
        // TODO(W-701): can we avoid asking for email?
        .requestEmail()
        .requestScopes(
          Scope(
            Scopes.DRIVE_APPFOLDER
          ), // used by [GoogleDriveKeyValueStore] for storing encrypted app backup
          Scope(
            Scopes.DRIVE_FILE
          ) // used by [GoogleDriveFileStore] for storing Emergency Access Kit PDF
        )
        .build()
    GoogleSignIn.getClient(platformContext.appContext, options)
  }
}
