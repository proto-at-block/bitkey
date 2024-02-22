package build.wallet.auth

import build.wallet.bitkey.account.LiteAccount
import build.wallet.bitkey.account.LiteAccountConfig
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CreateAccountClientErrorCode
import build.wallet.ktor.result.HttpError
import com.github.michaelbull.result.Result

/**
 * Manages creating a "lite" account by generating auth keys and then calling
 * the account creation endpoint with the auth keys (no spending keys).
 */
interface LiteAccountCreator {
  suspend fun createAccount(
    config: LiteAccountConfig,
  ): Result<LiteAccount, LiteAccountCreationError>
}

sealed class LiteAccountCreationError : Error() {
  /** Failed to create auth keys. Should happen rarely, if ever. */
  data class LiteAccountKeyGenerationError(
    override val cause: Throwable,
  ) : LiteAccountCreationError()

  sealed class LiteAccountCreationDatabaseError : LiteAccountCreationError() {
    /** Failed to save auth keys. Should happen rarely, if ever. */
    data class FailedToSaveAuthTokens(
      override val cause: Throwable,
    ) : LiteAccountCreationDatabaseError()

    /** Failed to save account. Should happen rarely, if ever. */
    data class FailedToSaveAccount(
      override val cause: Throwable,
    ) : LiteAccountCreationDatabaseError()
  }

  /** Failed to create account on the server. Expected to happen if there are connectivity issues. */
  data class LiteAccountCreationF8eError(
    val f8eError: F8eError<CreateAccountClientErrorCode>,
  ) : LiteAccountCreationError() {
    override val cause: Throwable? = f8eError.error.cause
  }

  /** Failed to auth on the server. Expected to happen if there are connectivity issues. */
  data class LiteAccountCreationAuthError(
    val authError: AuthError,
  ) : LiteAccountCreationError() {
    override val cause: Throwable? = authError.cause
  }

  val isConnectivityError: Boolean
    get() {
      return when (this) {
        is LiteAccountCreationF8eError -> f8eError is F8eError.ConnectivityError
        is LiteAccountCreationAuthError -> (authError as AuthNetworkError).cause is HttpError.NetworkError
        else -> false
      }
    }
}
