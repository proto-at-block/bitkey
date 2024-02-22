package build.wallet.auth

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bitkey.f8e.AccountId
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class AuthTokenDaoMock(
  turbine: (String) -> Turbine<Any>,
) : AuthTokenDao {
  val clearCalls = turbine("clear auth token calls")
  val setTokensCalls = turbine("set auth tokens calls")

  var tokensFlow = MutableStateFlow<AccountAuthTokens?>(null)

  override suspend fun getTokensOfScope(
    accountId: AccountId,
    scope: AuthTokenScope,
  ): Result<AccountAuthTokens?, Throwable> {
    return Ok(tokensFlow.value)
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
    tokensFlow.value = tokens
    setTokensCalls += SetTokensParams(accountId, tokens, scope)
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Throwable> {
    tokensFlow.value = null
    clearCalls += Unit
    return Ok(Unit)
  }

  fun reset() {
    tokensFlow.value = null
  }
}
