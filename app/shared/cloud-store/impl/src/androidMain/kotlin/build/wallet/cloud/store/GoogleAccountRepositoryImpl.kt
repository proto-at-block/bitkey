package build.wallet.cloud.store

import android.app.Application
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@BitkeyInject(AppScope::class)
class GoogleAccountRepositoryImpl(
  private val application: Application,
) : GoogleAccountRepository {
  override suspend fun currentAccount(): Result<GoogleAccount?, GoogleAccountError> {
    // Running on IO dispatcher because GoogleSignIn.getLastSignedInAccount is using shared preferences.
    return withContext(Dispatchers.IO) {
      catchingResult {
        GoogleSignIn.getLastSignedInAccount(application)?.let { credentials ->
          requireNotNull(credentials.account) {
            "Found logged in GoogleSignInAccount, but Account missing."
          }
          GoogleAccount(credentials)
        }
      }
        .mapError { GoogleAccountError(it) }
    }
  }
}
