package bitkey.onboarding

import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CreateAccountClientErrorCode
import build.wallet.auth.AuthError

sealed class FullAccountCreationError : Error() {
  sealed class AccountCreationDatabaseError : FullAccountCreationError() {
    data class FailedToSaveAuthTokens(
      override val cause: Throwable,
    ) : AccountCreationDatabaseError()

    data class FailedToSaveAccount(override val cause: Throwable) : AccountCreationDatabaseError()

    data class FailedToSaveKeybox(override val cause: Throwable) : AccountCreationDatabaseError()
  }

  data class ErrorGeneratingRecoveryAuthKey(
    override val cause: Throwable,
  ) : AccountCreationDatabaseError()

  data class AccountCreationF8eError(
    val f8eError: F8eError<CreateAccountClientErrorCode>,
  ) : FullAccountCreationError() {
    override val cause: Throwable? = f8eError.error.cause
  }

  data class AccountCreationAuthError(
    val authError: AuthError,
  ) : FullAccountCreationError() {
    override val cause: Throwable? = authError.cause
  }
}
