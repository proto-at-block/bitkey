package build.wallet.google.signin

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import build.wallet.catchingResult
import build.wallet.google.signin.GoogleSignInError.*
import build.wallet.logging.LogLevel.Error
import build.wallet.logging.LogLevel.Info
import build.wallet.logging.logInternal
import com.github.michaelbull.result.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.*
import com.google.android.gms.common.api.ApiException

/**
 * Contract for handling result from Google Sign In Activity.
 */
internal class GoogleSignInActivityResultContract(
  private val googleSignInClient: GoogleSignInClient,
) : ActivityResultContract<Unit, Result<GoogleSignInAccount, GoogleSignInError>>() {
  override fun createIntent(
    context: Context,
    input: Unit,
  ): Intent = googleSignInClient.signInIntent

  override fun parseResult(
    resultCode: Int,
    intent: Intent?,
  ): Result<GoogleSignInAccount, GoogleSignInError> {
    return catchingResult {
      GoogleSignIn.getSignedInAccountFromIntent(intent).also {
        check(it.isComplete) { "Expected google sign in task to be complete" }
      }
    }
      .mapError { error ->
        UnhandledError(
          message = "Failed to get GoogleSignInAccount from intent result.",
          cause = error
        )
      }
      .flatMap { task ->
        if (task.isSuccessful) {
          val account = task.result
          if (account.account == null) {
            Err(AndroidAccountMissing(message = "Account missing in GoogleSignInAccount. $account"))
          } else {
            Ok(account)
          }
        } else {
          val error =
            when (val exception = task.exception) {
              is ApiException -> {
                when (exception.statusCode) {
                  DEVELOPER_ERROR -> ConfigurationError(cause = exception)
                  SIGN_IN_FAILED -> SignInFailed(cause = exception)
                  SIGN_IN_CANCELLED -> SignInCanceled(cause = exception)
                  SIGN_IN_CURRENTLY_IN_PROGRESS -> SignInCurrentlyInProgress(cause = exception)
                  else ->
                    UnhandledError(
                      message = "Unhandled error code: ${exception.statusCode}",
                      cause = exception
                    )
                }
              }

              else -> UnhandledError(cause = exception)
            }

          Err(error)
        }
      }
      .onFailure {
        val logLevel =
          when (it) {
            is NetworkError -> Info
            is SignInCanceled -> Info
            is ConfigurationError -> Error
            is AndroidAccountMissing -> Error
            is SignInCurrentlyInProgress -> Error
            is SignInFailed -> Error
            is UnhandledError -> Error
            is GoogleSignInAccountMissing -> Error
          }
        logInternal(logLevel, throwable = it.cause) { it.message.orEmpty() }
      }
  }
}
