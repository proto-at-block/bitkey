package build.wallet.auth

import build.wallet.bitkey.f8e.AccountId
import build.wallet.catchingResult
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.clearWithResult
import build.wallet.store.putStringWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding

class AuthTokenDaoImpl(
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
) : AuthTokenDao {
  private suspend fun secureStore() = encryptedKeyValueStoreFactory.getOrCreate(KEYSTORE_NAME)

  override suspend fun getTokensOfScope(
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens?, Throwable> {
    return catchingResult {
      // Initialize the secure store
      val secureStore = secureStore()

      val accessToken =
        secureStore
          .getStringOrNull(accessTokenKey(accountId, scope))
          ?.let { AccessToken(it) }

      val refreshToken =
        secureStore
          .getStringOrNull(refreshTokenKey(accountId, scope))
          ?.let { RefreshToken(it) }

      if (accessToken != null && refreshToken != null) {
        AccountAuthTokens(accessToken = accessToken, refreshToken = refreshToken)
      } else {
        // If we couldn't find the tokens for the Global scope, fall back to the legacy keys
        // for the Global tokens, which didn't include a scope. Since these tokens expire
        // after 30 days, we should be able to remove this fallback after a similar amount of time.
        if (scope == AuthTokenScope.Global) {
          getLegacyGlobalTokens(accountId)
        } else {
          null
        }
      }
    }
      .logFailure { "Error loading auth tokens for $accountId" }
  }

  override suspend fun setTokensOfScope(
    accountId: AccountId,
    tokens: AccountAuthTokens,
    scope: AuthTokenScope,
  ): Result<Unit, Throwable> =
    coroutineBinding {
      // Initialize the secure store
      val secureStore = secureStore()
      // Atomically write both refresh and access tokens
      secureStore
        .putStringWithResult(key = accessTokenKey(accountId, scope), value = tokens.accessToken.raw)
        .bind()
      secureStore
        .putStringWithResult(
          key = refreshTokenKey(accountId, scope),
          value = tokens.refreshToken.raw
        )
        .bind()
    }.logFailure { "Error setting auth tokens for $accountId" }

  override suspend fun clear(): Result<Unit, Throwable> {
    return secureStore().clearWithResult()
  }

  private fun accessTokenKey(
    accountId: AccountId,
    scope: AuthTokenScope?,
  ): String {
    return when (scope) {
      null -> "accessToken_${accountId.serverId}"
      AuthTokenScope.Global -> "accessToken_global_${accountId.serverId}"
      AuthTokenScope.Recovery -> "accessToken_recovery_${accountId.serverId}"
    }
  }

  private fun refreshTokenKey(
    accountId: AccountId,
    scope: AuthTokenScope?,
  ): String {
    return when (scope) {
      null -> "refreshToken_${accountId.serverId}"
      AuthTokenScope.Global -> "refreshToken_global_${accountId.serverId}"
      AuthTokenScope.Recovery -> "refreshToken_recovery_${accountId.serverId}"
    }
  }

  private suspend fun getLegacyGlobalTokens(accountId: AccountId): AccountAuthTokens? {
    val secureStore = secureStore()
    val legacyAccessToken =
      secureStore
        .getStringOrNull(accessTokenKey(accountId, null))
        ?.let { AccessToken(it) }
        ?: return null

    val legacyRefreshToken =
      secureStore
        .getStringOrNull(refreshTokenKey(accountId, null))
        ?.let { RefreshToken(it) }
        ?: return null

    log(LogLevel.Warn) { "Falling back to legacy auth token keys in AuthTokenDao" }
    return AccountAuthTokens(accessToken = legacyAccessToken, refreshToken = legacyRefreshToken)
  }

  companion object {
    const val KEYSTORE_NAME = "AuthTokenStore"
  }
}
