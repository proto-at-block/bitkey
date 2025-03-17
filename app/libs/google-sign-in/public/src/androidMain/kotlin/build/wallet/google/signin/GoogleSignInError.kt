package build.wallet.google.signin

/**
 * Describes a failure during a Google Sign In - either something is wrong with our Google
 * integration, customer canceled signed, or there's some other error.
 *
 * Google Sign In errors are described here:
 * - [CommonStatusCodes](https://developers.google.com/android/reference/com/google/android/gms/common/api/CommonStatusCodes)
 * - [GoogleSignInStatusCodes](https://developers.google.com/android/reference/com/google/android/gms/auth/api/signin/GoogleSignInStatusCodes)
 */
sealed class GoogleSignInError : Error() {
  /**
   * The application is misconfigured. This error is not recoverable and will be treated as fatal.
   * The developer should look at the logs after this to determine more actionable information.
   *
   * Error code: 10
   */
  data class ConfigurationError(
    override val message: String = "Google Sign In integration is misconfigured",
    override val cause: Throwable? = null,
  ) : GoogleSignInError()

  /**
   * Successfully logged in but [GoogleSignInAccount] did not have Android [Account] instance.
   */
  data class AndroidAccountMissing(
    override val message: String = "Successfully signed in but did not find Android Account in GoogleSignInAccount",
    override val cause: Throwable? = null,
  ) : GoogleSignInError()

  /**
   * Successfully logged in but [GoogleSignInAccount] was missing.
   */
  data class GoogleSignInAccountMissing(
    override val message: String = "Successfully signed in but did not find GoogleSignInAccount",
    override val cause: Throwable?,
  ) : GoogleSignInError()

  /**
   * A network error occurred. Retrying should resolve the problem.
   *
   * Error code: 7
   */
  data class NetworkError(
    override val message: String = "Google Sign In network error",
    override val cause: Throwable,
  ) : GoogleSignInError()

  /**
   * The sign in attempt didn't succeed with the current account.
   *
   * Unlike CommonStatusCodes.SIGN_IN_REQUIRED. when seeing this error code, there is nothing user
   * can do to recover from the sign in failure. Switching to another account may or may not help.
   * Check adb log to see details if any.
   *
   * Error code: 12500
   */
  data class SignInFailed(
    override val message: String = "Google Sign In Failed",
    override val cause: Throwable,
  ) : GoogleSignInError()

  /**
   * The sign in was canceled by the user. i.e. user canceled some of the sign in resolutions,
   * e.g. account picking or OAuth consent.
   *
   * Error code: 12501
   */
  data class SignInCanceled(
    override val cause: Throwable,
  ) : GoogleSignInError() {
    override val message: String = "Google Sign In Canceled"
  }

  /**
   * A sign in process is currently in progress and the current one cannot continue. e.g.
   * the user clicks the SignInButton multiple times and more than one sign in intent was launched.
   *
   * Error code: 12502.
   */
  data class SignInCurrentlyInProgress(
    override val cause: Throwable,
  ) : GoogleSignInError() {
    override val message: String = "Google Sign In is already in progress"
  }

  /**
   * An error that we don't handle.
   */
  data class UnhandledError(
    override val message: String = "Unhandled Google Sign In error",
    override val cause: Throwable?,
  ) : GoogleSignInError()
}
