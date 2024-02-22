package build.wallet.cloud.store

import build.wallet.catching
import build.wallet.platform.PlatformContext
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleAccountRepositoryImpl(
  private val platformContext: PlatformContext,
) : GoogleAccountRepository {
  override suspend fun currentAccount(): Result<GoogleAccount?, GoogleAccountError> {
    // Running on IO dispatcher because GoogleSignIn.getLastSignedInAccount is using shared preferences.
    return withContext(Dispatchers.IO) {
      Result
        .catching {
          GoogleSignIn.getLastSignedInAccount(platformContext.appContext)?.let {
              credentials ->
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
