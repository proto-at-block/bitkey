package build.wallet.google.signin

import build.wallet.catchingResult
import build.wallet.logging.LogLevel.Error
import build.wallet.logging.log
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class GoogleSignOutActionImpl(
  private val googleSignInClientProvider: GoogleSignInClientProvider,
) : GoogleSignOutAction {
  override suspend fun signOut(): Result<Unit, GoogleSignOutError> {
    log { "Signing out from Google accounts" }
    return catchingResult {
      withTimeout(10.seconds) {
        withContext(Dispatchers.IO) {
          with(googleSignInClientProvider.clientForGoogleDrive) {
            // Revoking access disconnects customer's Google account from our app.
            revokeAccess().continueWith { signOut() }
          }.await()
        }
      }
    }
      .map {
        log { "Successfully logged out from Google account and revoked access." }
        // Noop: Sign out action was successful.
      }
      .mapError { error ->
        log(Error, throwable = error) { "Failed to sign out from Google account." }
        GoogleSignOutError("Google Sign Out failed. $error")
      }
  }
}
