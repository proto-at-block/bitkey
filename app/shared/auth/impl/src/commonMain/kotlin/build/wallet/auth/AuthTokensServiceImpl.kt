package build.wallet.auth

import build.wallet.availability.AuthSignatureStatus.Authenticated
import build.wallet.availability.AuthSignatureStatus.Unauthenticated
import build.wallet.availability.F8eAuthSignatureStatusProvider
import build.wallet.bitkey.f8e.AccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.AuthF8eClient
import build.wallet.logging.logFailure
import build.wallet.platform.config.AppVariant
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding

@BitkeyInject(AppScope::class)
class AuthTokensServiceImpl(
  private val authTokenDao: AuthTokenDao,
  private val accountAuthenticator: AccountAuthenticator,
  private val authF8eClient: AuthF8eClient,
  private val appAuthPublicKeyProvider: AppAuthPublicKeyProvider,
  private val f8eAuthSignatureStatusProvider: F8eAuthSignatureStatusProvider,
  private val appVariant: AppVariant,
) : AuthTokensService {
  override suspend fun refreshAccessTokenWithApp(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens, Error> {
    return coroutineBinding {
      val appAuthPublicKey = appAuthPublicKeyProvider
        .getAppAuthPublicKeyFromAccountOrRecovery(f8eEnvironment, accountId, scope)
        // Allow access token to be refreshed using the refresh token even if we don't have an
        // account or recovery. If the refresh token is expired, we will fail later in this flow.
        .recoverIf(
          predicate = { it is AccountMissing },
          transform = { null }
        )
        .bind()

      // Retrieve current auth tokens from local storage
      val authTokens = authTokenDao
        .getTokensOfScope(accountId, scope)
        .toErrorIfNull {
          AuthStorageError(message = "Unable to find authToken for account: $accountId")
        }
        .mapError(::AuthStorageError)
        .bind()

      // Refresh auth tokens with f8e
      val newTokens = authF8eClient
        .refreshToken(f8eEnvironment, authTokens.refreshToken)
        .mapError { AuthNetworkError(cause = it) }
        .orElse {
          // The refresh token we passed to f8e is likely expired, so we need to re-authenticate.
          if (appAuthPublicKey == null) {
            // If we couldn't find an account or pending recovery, we won't be able to authenticate
            Err(AccountMissing)
          } else {
            accountAuthenticator
              .appAuth(f8eEnvironment, appAuthPublicKey, scope)
              .logAuthFailure { "Error when re-authenticating with app key" }
              .map { it.authTokens }
              .onSuccess {
                // If able to authenticate with app key, mark the auth signature as authenticated
                f8eAuthSignatureStatusProvider.updateAuthSignatureStatus(Authenticated)
              }
              .onFailure { error ->
                // If unable to authenticate with app key due to a signature mismatch, mark the auth
                // status as unauthenticated
                if (error is AuthSignatureMismatch) {
                  f8eAuthSignatureStatusProvider.updateAuthSignatureStatus(Unauthenticated)
                }
              }
          }
        }
        .bind()

      // Update auth tokens in local storage
      authTokenDao
        .setTokensOfScope(accountId, newTokens, scope)
        .mapError(::AuthStorageError)
        .bind()

      newTokens
    }.logFailure { "Error refreshing access token for active or onboarding keybox for $accountId" }
  }

  override suspend fun getTokens(
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens?, Throwable> {
    return authTokenDao.getTokensOfScope(accountId, scope)
  }

  /**
   * Sets the given tokens for the given account and given scope.
   */
  override suspend fun setTokens(
    accountId: AccountId,
    tokens: AccountAuthTokens,
    scope: AuthTokenScope,
  ): Result<Unit, Throwable> {
    return authTokenDao.setTokensOfScope(accountId, tokens, scope)
  }

  override suspend fun clear(): Result<Unit, Throwable> =
    coroutineBinding {
      ensure(appVariant != AppVariant.Customer) {
        Error("Cannot clear auth tokens in production build.")
      }

      authTokenDao.clear().bind()
    }
}
