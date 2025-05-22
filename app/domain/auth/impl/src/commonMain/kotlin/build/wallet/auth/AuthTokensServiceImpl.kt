package build.wallet.auth

import bitkey.account.AccountConfigService
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope
import build.wallet.availability.AuthSignatureStatus.Authenticated
import build.wallet.availability.AuthSignatureStatus.Unauthenticated
import build.wallet.availability.F8eAuthSignatureStatusProvider
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.crypto.PublicKey
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
  private val accountConfigService: AccountConfigService,
) : AuthTokensService {
  override suspend fun refreshAccessTokenWithApp(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens, Error> {
    return coroutineBinding {
      val appAuthPublicKey = appAuthPublicKey(accountId, scope).bind()

      // Retrieve current auth tokens from local storage
      val authTokens = authTokenDao
        .getTokensOfScope(accountId, scope)
        .toErrorIfNull {
          AuthStorageError(message = "Unable to find authToken for account: $accountId")
        }
        .mapError(::AuthStorageError)
        .bind()

      validateF8eEnvironment(f8eEnvironment).bind()

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
            performAppAuth(appAuthPublicKey, scope)
          }
        }
        .bind()

      // Update auth tokens in local storage
      storeAuthTokens(accountId, newTokens, scope).bind()

      newTokens
    }.logFailure { "Error refreshing access token for active or onboarding keybox for $accountId" }
  }

  override suspend fun refreshRefreshTokenWithApp(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens, Error> {
    return coroutineBinding {
      val appAuthPublicKey = appAuthPublicKey(accountId, scope).bind()
      validateF8eEnvironment(f8eEnvironment).bind()

      ensure(appAuthPublicKey != null) {
        Error(AccountMissing)
      }

      // Reauthenticate with f8e
      val newTokens = performAppAuth(appAuthPublicKey, scope).bind()
      // Update auth tokens in local storage
      storeAuthTokens(accountId, newTokens, scope).bind()

      newTokens
    }.logFailure { "Error refreshing refresh token for active or onboarding keybox for $accountId" }
  }

  private suspend fun appAuthPublicKey(
    accountId: AccountId,
    scope: AuthTokenScope,
  ) = appAuthPublicKeyProvider
    .getAppAuthPublicKeyFromAccountOrRecovery(accountId, scope)
    // Allow tokens to be refreshed even if we don't have an account or recovery.
    .recoverIf(
      predicate = { it is AccountMissing },
      transform = { null }
    )

  private suspend fun storeAuthTokens(
    accountId: AccountId,
    newTokens: AccountAuthTokens,
    scope: AuthTokenScope,
  ) = authTokenDao
    .setTokensOfScope(accountId, newTokens, scope)
    .mapError(::AuthStorageError)

  private suspend fun validateF8eEnvironment(
    f8eEnvironment: F8eEnvironment,
  ): Result<F8eEnvironment, Error> {
    return coroutineBinding {
      val accountConfig = accountConfigService.activeOrDefaultConfig().value
      ensure(accountConfig.f8eEnvironment == f8eEnvironment) {
        Error(
          "Requested F8eEnvironment ($f8eEnvironment) does not match app's F8eEnvironment (${accountConfig.f8eEnvironment})"
        )
      }
      accountConfig.f8eEnvironment
    }
  }

  private suspend fun performAppAuth(
    appAuthPublicKey: PublicKey<out AppAuthKey>,
    scope: AuthTokenScope,
  ) = accountAuthenticator
    .appAuth(appAuthPublicKey, scope)
    .logAuthFailure { "Error when re-authenticating with App Key" }
    .map { it.authTokens }
    .onSuccess {
      // If able to authenticate with App Key, mark the auth signature as authenticated
      f8eAuthSignatureStatusProvider.updateAuthSignatureStatus(Authenticated)
    }
    .onFailure { error ->
      // If unable to authenticate with App Key due to a signature mismatch, mark the auth
      // status as unauthenticated
      if (error is AuthSignatureMismatch) {
        f8eAuthSignatureStatusProvider.updateAuthSignatureStatus(Unauthenticated)
      }
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
