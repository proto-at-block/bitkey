package build.wallet.auth

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.KeyCrossDraft.WithAppKeysAndHardwareKeys
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CreateAccountClientErrorCode
import com.github.michaelbull.result.Result

interface FullAccountCreator {
  /**
   * Given brand new app and hardware keys, create a brand new [FullAccount]
   */
  suspend fun createAccount(
    keyCrossDraft: WithAppKeysAndHardwareKeys,
  ): Result<FullAccount, AccountCreationError>
}

sealed class AccountCreationError : Error() {
  sealed class AccountCreationDatabaseError : AccountCreationError() {
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
  ) : AccountCreationError() {
    override val cause: Throwable? = f8eError.error.cause
  }

  data class AccountCreationAuthError(
    val authError: AuthError,
  ) : AccountCreationError() {
    override val cause: Throwable? = authError.cause
  }
}
