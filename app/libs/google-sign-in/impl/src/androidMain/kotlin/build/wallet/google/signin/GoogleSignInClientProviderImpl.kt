package build.wallet.google.signin

import android.app.Application
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope

@BitkeyInject(AppScope::class)
class GoogleSignInClientProviderImpl(
  private val application: Application,
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
          ) // used by [GoogleDriveFileStore] for storing Emergency Exit Kit PDF
        )
        .build()
    GoogleSignIn.getClient(application, options)
  }
}
