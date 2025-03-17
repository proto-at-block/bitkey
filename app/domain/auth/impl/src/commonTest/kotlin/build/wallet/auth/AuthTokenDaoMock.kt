package build.wallet.auth

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope
import build.wallet.bitkey.f8e.AccountId
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class AuthTokenDaoMock(
  turbine: (String) -> Turbine<Any>,
) : AuthTokenDao {
  val clearCalls = turbine("clear auth token calls")
  val setTokensCalls = turbine("set auth tokens calls")

  private val defaultSetTokensResult: Result<Unit, Throwable> = Ok(Unit)
  var setTokensResult = defaultSetTokensResult

  private val defaultGetTokensResult: Result<AccountAuthTokens?, Throwable> = Ok(null)
  var getTokensResult = defaultGetTokensResult

  override suspend fun getTokensOfScope(
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens?, Throwable> {
    return getTokensResult
  }

  data class SetTokensParams(
    val accountId: AccountId,
    val tokens: AccountAuthTokens,
    val scope: AuthTokenScope,
  )

  override suspend fun setTokensOfScope(
    accountId: AccountId,
    tokens: AccountAuthTokens,
    scope: AuthTokenScope,
  ): Result<Unit, Throwable> {
    getTokensResult = Ok(tokens)
    setTokensCalls += SetTokensParams(accountId, tokens, scope)
    return setTokensResult
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    getTokensResult = Ok(null)
    clearCalls += Unit
    return Ok(Unit)
  }

  fun reset() {
    getTokensResult = defaultGetTokensResult
    setTokensResult = defaultSetTokensResult
  }
}
