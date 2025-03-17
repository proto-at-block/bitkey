package build.wallet.google.signin

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.*
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.google.android.gms.auth.api.signin.GoogleSignInAccount

@BitkeyInject(AppScope::class)
class GoogleSignInLauncherImpl(
  private val googleSignInClientProvider: GoogleSignInClientProvider,
) : GoogleSignInLauncher {
  @SuppressLint("ComposableNaming")
  @Composable
  override fun launchedGoogleSignIn(
    onSignInSuccess: (GoogleSignInAccount) -> Unit,
    onSignInFailure: (GoogleSignInError) -> Unit,
  ) {
    val signInRequestLauncher =
      rememberLauncherForActivityResult(
        contract =
          GoogleSignInActivityResultContract(
            googleSignInClientProvider.clientForGoogleDrive
          )
      ) { account ->
        account
          .onSuccess { onSignInSuccess(it) }
          .onFailure { onSignInFailure(it) }
      }

    LaunchedEffect("launch-sign-in") {
      logDebug { "Launching Google Sign In" }
      signInRequestLauncher.launch(Unit)
    }
  }
}
