package build.wallet.google.signin

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import build.wallet.catchingResult
import build.wallet.google.signin.GoogleSignInError.AndroidAccountMissing
import build.wallet.google.signin.GoogleSignInError.ConfigurationError
import build.wallet.google.signin.GoogleSignInError.GoogleSignInAccountMissing
import build.wallet.google.signin.GoogleSignInError.NetworkError
import build.wallet.google.signin.GoogleSignInError.SignInCanceled
import build.wallet.google.signin.GoogleSignInError.SignInCurrentlyInProgress
import build.wallet.google.signin.GoogleSignInError.SignInFailed
import build.wallet.google.signin.GoogleSignInError.UnhandledError
import build.wallet.logging.LogLevel.Error
import build.wallet.logging.LogLevel.Info
import build.wallet.logging.log
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.DEVELOPER_ERROR
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.SIGN_IN_CANCELLED
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes.SIGN_IN_FAILED
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
        log(logLevel, throwable = it.cause) { it.message.orEmpty() }
      }
  }
}
