package build.wallet.google.signin

import build.wallet.coroutines.withTimeoutResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.*
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
class GoogleSignOutActionImpl(
  private val googleSignInClientProvider: GoogleSignInClientProvider,
) : GoogleSignOutAction {
  override suspend fun signOut(): Result<Unit, GoogleSignOutError> {
    logDebug { "Signing out from Google accounts" }
    return withTimeoutResult(10.seconds) {
      withContext(Dispatchers.IO) {
        with(googleSignInClientProvider.clientForGoogleDrive) {
          // Revoking access disconnects customer's Google account from our app.
          revokeAccess().continueWith { signOut() }
        }.await()
      }
    }
      .map {
        logDebug { "Successfully logged out from Google account and revoked access." }
        // Noop: Sign out action was successful.
      }
      .logFailure { "Failed to sign out from Google account" }
      .mapError { error ->
        GoogleSignOutError("Google Sign Out failed. $error")
      }
  }
}
