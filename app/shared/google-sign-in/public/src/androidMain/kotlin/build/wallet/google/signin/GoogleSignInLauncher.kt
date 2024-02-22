package build.wallet.google.signin

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

interface GoogleSignInLauncher {
  /**
   * Launches Google Sign In Activity as a declared, stable side effect.
   *
   * @param onSignInSuccess - callback for handling successful sign in result.
   * @param onSignInFailure - callback for handling failed sign in result.
   */
  @SuppressLint("ComposableNaming")
  @Composable
  fun launchedGoogleSignIn(
    onSignInSuccess: (GoogleSignInAccount) -> Unit,
    onSignInFailure: (GoogleSignInError) -> Unit,
  )
}
