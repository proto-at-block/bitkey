package build.wallet.auth

import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.platform.random.uuid
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fake implementation of [AuthTokensService] that manages auth tokens in-memory map.
 */
class AuthTokensServiceFake : AuthTokensService {
  private val lock = Mutex()
  private val allTokens = mutableMapOf<Pair<AccountId, AuthTokenScope>, AccountAuthTokens>()

  var refreshAccessTokenError: Error? = null

  override suspend fun refreshAccessTokenWithApp(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens, Error> {
    return lock.withLock {
      refreshAccessTokenError?.let { return Err(it) }

      val currentTokens = allTokens[accountId to scope]
        ?: return Err(Error("No $scope tokens found for $accountId"))

      val newTokens = currentTokens.copy(
        accessToken = AccessToken("${currentTokens.accessToken.raw}-${uuid()}")
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
    }
  }
}
