package build.wallet.onboarding

import build.wallet.auth.AuthError
import build.wallet.bitkey.account.OnboardingSoftwareAccount
import build.wallet.bitkey.account.SoftwareAccountConfig
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.crypto.PublicKey
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CreateAccountClientErrorCode
import com.github.michaelbull.result.Result

/**
 * Manages creating a software account by generating auth keys and then calling
 * the account creation endpoint with the auth keys (no spending keys).
 */
interface SoftwareAccountCreator {
  suspend fun createAccount(
    authKey: PublicKey<AppGlobalAuthKey>,
    recoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
    config: SoftwareAccountConfig,
  ): Result<OnboardingSoftwareAccount, SoftwareAccountCreationError>
}

sealed class SoftwareAccountCreationError : Error() {
  /** Failed to create auth keys. Should happen rarely, if ever. */

  sealed class SoftwareAccountCreationDatabaseError : SoftwareAccountCreationError() {
    /** Failed to save auth keys. Should happen rarely, if ever. */
    data class FailedToSaveAuthTokens(
      override val cause: Throwable,
    ) : SoftwareAccountCreationDatabaseError()

    /** Failed to save account. Should happen rarely, if ever. */
    data class FailedToSaveAccount(
      override val cause: Throwable,
    ) : SoftwareAccountCreationDatabaseError()
  }

  /** Failed to create account on the server. Expected to happen if there are connectivity issues. */
  data class SoftwareAccountCreationF8eError(
    val f8eError: F8eError<CreateAccountClientErrorCode>,
  ) : SoftwareAccountCreationError() {
    override val cause: Throwable? = f8eError.error.cause
  }

  /** Failed to auth on the server. Expected to happen if there are connectivity issues. */
  data class SoftwareAccountCreationAuthError(
    val authError: AuthError,
  ) : SoftwareAccountCreationError() {
    override val cause: Throwable? = authError.cause
  }
}
