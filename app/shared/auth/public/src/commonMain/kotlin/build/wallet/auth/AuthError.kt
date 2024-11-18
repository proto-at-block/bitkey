package build.wallet.auth

import build.wallet.ktor.result.NetworkingError

sealed class AuthError : Error()

data class AuthStorageError(
  override val cause: Throwable? = null,
  override val message: String? = cause?.message,
) : AuthError()

data class AuthProtocolError(
  override val message: String? = null,
  override val cause: Throwable? = null,
) : AuthError()

data class AuthNetworkError(
  override val message: String? = null,
  override val cause: NetworkingError? = null,
) : AuthError()

/** The api has stated there is a mismatch between the sent key and expected key */
data object AuthSignatureMismatch : AuthError()

data class FailedToReadAccountStatus(override val cause: Error) : AuthError()

data class FailedToReadRecoveryStatus(override val cause: Error) : AuthError()

data object AccountMissing : AuthError()

data object AppRecoveryAuthPublicKeyMissing : AuthError()

data object RequestGlobalScopeForLiteAccount : AuthError()

data class UnhandledError(override val cause: Throwable) : AuthError()
