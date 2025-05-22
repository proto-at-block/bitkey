package build.wallet.auth

import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope
import bitkey.auth.RefreshToken
import build.wallet.bitkey.f8e.AccountId
import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.logging.logWarn
import build.wallet.store.EncryptedKeyValueStoreFactory
import build.wallet.store.clearWithResult
import build.wallet.store.putStringWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.datetime.Instant

@BitkeyInject(AppScope::class)
class AuthTokenDaoImpl(
  private val encryptedKeyValueStoreFactory: EncryptedKeyValueStoreFactory,
) : AuthTokenDao {
  private suspend fun secureStore() = encryptedKeyValueStoreFactory.getOrCreate(KEYSTORE_NAME)

  override suspend fun getTokensOfScope(
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens?, Throwable> {
    return catchingResult {
      val secureStore = secureStore()

      val accessToken = secureStore
        .getStringOrNull(accessTokenKey(accountId, scope))
        ?.let { AccessToken(it) }

      val accessTokenExpiresAt = secureStore
        .getStringOrNull(accessTokenExpiresAtKey(accountId, scope))
        ?.let { expiresAt ->
          try {
            Instant.parse(expiresAt)
          } catch (_: IllegalArgumentException) {
            // In case of a parsing error, simply fall back to no expiry being available.
            logWarn { "Failed to parse accessTokenExpiresAt" }
            null
          }
        }

      val refreshToken = secureStore
        .getStringOrNull(refreshTokenKey(accountId, scope))
        ?.let { RefreshToken(it) }

      val refreshTokenExpiresAt = secureStore
        .getStringOrNull(refreshTokenExpiresAtKey(accountId, scope))
        ?.let { expiresAt ->
          try {
            Instant.parse(expiresAt)
          } catch (_: IllegalArgumentException) {
            // In case of a parsing error, simply fall back to no expiry being available.
            logWarn { "Failed to parse refreshTokenExpiresAt" }
            null
          }
        }

      if (accessToken != null && refreshToken != null) {
        AccountAuthTokens(
          accessToken = accessToken,
          refreshToken = refreshToken,
          accessTokenExpiresAt = accessTokenExpiresAt,
          refreshTokenExpiresAt = refreshTokenExpiresAt
        )
      } else {
        null
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
      val secureStore = secureStore()
      secureStore
        .putStringWithResult(key = accessTokenKey(accountId, scope), value = tokens.accessToken.raw)
        .bind()

      tokens.accessTokenExpiresAt?.let {
        secureStore
          .putStringWithResult(
            key = accessTokenExpiresAtKey(accountId, scope),
            value = it.toString()
          )
          .bind()
      }

      tokens.refreshTokenExpiresAt?.let {
        secureStore
          .putStringWithResult(
            key = refreshTokenExpiresAtKey(accountId, scope),
            value = it.toString()
          )
          .bind()
      }

      secureStore
        .putStringWithResult(
          key = refreshTokenKey(accountId, scope),
          value = tokens.refreshToken.raw
        )
        .bind()
    }
      .logFailure { "Error setting auth tokens for $accountId" }

  override suspend fun clear(): Result<Unit, Throwable> {
    return secureStore().clearWithResult()
  }

  private fun accessTokenKey(
    accountId: AccountId,
    scope: AuthTokenScope,
  ): String {
    return when (scope) {
      AuthTokenScope.Global -> "accessToken_global_${accountId.serverId}"
      AuthTokenScope.Recovery -> "accessToken_recovery_${accountId.serverId}"
    }
  }

  private fun accessTokenExpiresAtKey(
    accountId: AccountId,
    scope: AuthTokenScope,
  ): String {
    return when (scope) {
      AuthTokenScope.Global -> "accessToken_expiresAt_global_${accountId.serverId}"
      AuthTokenScope.Recovery -> "accessToken_expiresAt_recovery_${accountId.serverId}"
    }
  }

  private fun refreshTokenKey(
    accountId: AccountId,
    scope: AuthTokenScope,
  ): String {
    return when (scope) {
      AuthTokenScope.Global -> "refreshToken_global_${accountId.serverId}"
      AuthTokenScope.Recovery -> "refreshToken_recovery_${accountId.serverId}"
    }
  }

  private fun refreshTokenExpiresAtKey(
    accountId: AccountId,
    scope: AuthTokenScope,
  ): String {
    return when (scope) {
      AuthTokenScope.Global -> "refreshToken_expiresAt_global_${accountId.serverId}"
      AuthTokenScope.Recovery -> "refreshToken_expiresAt_recovery_${accountId.serverId}"
    }
  }

  companion object {
    const val KEYSTORE_NAME = "AuthTokenStore"
  }
}
