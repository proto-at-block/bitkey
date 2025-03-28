package build.wallet.auth

import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope.Global
import bitkey.auth.AuthTokenScope.Recovery
import bitkey.auth.RefreshToken
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.store.EncryptedKeyValueStoreFactoryFake
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import kotlinx.datetime.Instant

class AuthTokenDaoImplTests : FunSpec({
  val encryptedKeyValueStoreFactory = EncryptedKeyValueStoreFactoryFake()
  val dao = AuthTokenDaoImpl(encryptedKeyValueStoreFactory)
  val accountId = FullAccountId("test-account")

  beforeTest {
    encryptedKeyValueStoreFactory.reset()
  }

  test("getTokensOfScope returns null when no tokens stored") {
    dao.getTokensOfScope(accountId, Global).shouldBeOk(null)
  }

  test("getTokensOfScope handles null expiry") {
    val tokens = AccountAuthTokens(
      accessToken = AccessToken("access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = null
    )
    dao.setTokensOfScope(accountId, tokens, Global).shouldBeOk()
    dao.getTokensOfScope(accountId, Global).shouldBeOk(tokens)
  }

  test("handles multiple scopes") {
    val globalTokens = AccountAuthTokens(
      accessToken = AccessToken("access-token-global"),
      refreshToken = RefreshToken("refresh-token-global"),
      accessTokenExpiresAt = Instant.parse("2025-03-11T19:34:45Z")
    )
    dao.setTokensOfScope(accountId, globalTokens, Global).shouldBeOk()

    val recoveryTokens = AccountAuthTokens(
      accessToken = AccessToken("access-token-recovery"),
      refreshToken = RefreshToken("refresh-token-recovery"),
      accessTokenExpiresAt = Instant.parse("2025-03-12T19:34:45Z")
    )
    dao.setTokensOfScope(accountId, recoveryTokens, Recovery).shouldBeOk()

    dao.getTokensOfScope(accountId, Global).shouldBeOk(globalTokens)
    dao.getTokensOfScope(accountId, Recovery).shouldBeOk(recoveryTokens)
  }
})
