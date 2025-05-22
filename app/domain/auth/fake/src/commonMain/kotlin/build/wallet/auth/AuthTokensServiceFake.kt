package build.wallet.auth

import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope
import bitkey.auth.RefreshToken
import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.platform.random.uuid
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Fake implementation of [AuthTokensService] that manages auth tokens in-memory map.
 */
class AuthTokensServiceFake : AuthTokensService {
  private val clock = ClockFake()
  private val lock = Mutex()
  private val allTokens = mutableMapOf<Pair<AccountId, AuthTokenScope>, AccountAuthTokens>()

  var refreshAccessTokenError: Error? = null
  var refreshAccessTokenTokens: AccountAuthTokens? = null
  var refreshRefreshTokenTokens: AccountAuthTokens? = null

  override suspend fun refreshAccessTokenWithApp(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens, Error> {
    return lock.withLock {
      refreshAccessTokenError?.let { return Err(it) }

      val currentTokens = allTokens[accountId to scope]
        ?: return Err(Error("No $scope tokens found for $accountId"))

      val newTokens = refreshAccessTokenTokens ?: currentTokens.copy(
        accessToken = AccessToken("${currentTokens.accessToken.raw}-${uuid()}"),
        accessTokenExpiresAt = clock.now().plus(5.minutes)
      )

      allTokens[accountId to scope] = newTokens
      Ok(newTokens)
    }
  }

  override suspend fun refreshRefreshTokenWithApp(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens, Error> {
    return lock.withLock {
      val currentTokens = allTokens[accountId to scope]
        ?: return Err(Error("No $scope tokens found for $accountId"))

      val newTokens = refreshRefreshTokenTokens ?: currentTokens.copy(
        accessToken = AccessToken("${currentTokens.accessToken.raw}-${uuid()}"),
        refreshToken = RefreshToken("${currentTokens.refreshToken.raw}-${uuid()}"),
        accessTokenExpiresAt = clock.now().plus(5.minutes),
        refreshTokenExpiresAt = clock.now().plus(30.days)
      )

      allTokens[accountId to scope] = newTokens
      Ok(newTokens)
    }
  }

  override suspend fun getTokens(
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens?, Throwable> {
    return lock.withLock {
      Ok(allTokens[accountId to scope])
    }
  }

  var setTokensError: Error? = null

  override suspend fun setTokens(
    accountId: AccountId,
    tokens: AccountAuthTokens,
    scope: AuthTokenScope,
  ): Result<Unit, Throwable> {
    return lock.withLock {
      setTokensError?.let { return Err(it) }
      allTokens[accountId to scope] = tokens
      Ok(Unit)
    }
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    return lock.withLock {
      allTokens.clear()
      Ok(Unit)
    }
  }

  suspend fun reset() {
    lock.withLock {
      allTokens.clear()
      setTokensError = null
      refreshAccessTokenError = null
      refreshAccessTokenTokens = null
      refreshRefreshTokenTokens = null
    }
  }
}
