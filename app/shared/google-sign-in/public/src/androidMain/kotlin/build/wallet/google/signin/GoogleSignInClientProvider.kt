package build.wallet.google.signin

import com.google.android.gms.auth.api.signin.GoogleSignInClient

interface GoogleSignInClientProvider {
  /**
   * Provides an instance of [GoogleSignInClient] for signing into Google account with permissions
   * scoped to access to app's data on Google Drive.
   */
  val clientForGoogleDrive: GoogleSignInClient
}
