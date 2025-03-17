package build.wallet.google.signin

import com.github.michaelbull.result.Result

interface GoogleSignOutAction {
  /**
   * Explicitly revokes access and signs out from a Google account,
   * if logged in.
   *
   * If sign out action is not complete within 10 seconds, returns failure.
   */
  suspend fun signOut(): Result<Unit, GoogleSignOutError>
}
