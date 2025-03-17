package build.wallet.onboarding

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CreateAccountClientErrorCode
import build.wallet.auth.AuthError
import build.wallet.bitkey.account.OnboardingSoftwareAccount
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.crypto.PublicKey
import com.github.michaelbull.result.Result

/**
 * Manages creating a software account by generating auth keys and then calling
 * the account creation endpoint with the auth keys (no spending keys).
 */
interface CreateSoftwareAccountService {
  suspend fun createAccount(
    authKey: PublicKey<AppGlobalAuthKey>,
    recoveryAuthKey: PublicKey<AppRecoveryAuthKey>,
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
