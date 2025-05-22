package build.wallet.auth

import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope.Global
import bitkey.auth.AuthTokenScope.Recovery
import bitkey.auth.RefreshToken
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.store.EncryptedKeyValueStoreFactoryFake
import build.wallet.testing.shouldBeOk
import com.russhwolf.settings.ExperimentalSettingsApi
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalSettingsApi::class)
class AuthTokenDaoImplTests : FunSpec({
  val encryptedKeyValueStoreFactory = EncryptedKeyValueStoreFactoryFake()
  val dao = AuthTokenDaoImpl(encryptedKeyValueStoreFactory)
  val accountId = FullAccountId("test-account")
  val now = Instant.parse("2025-03-11T19:34:45Z")

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
      accessTokenExpiresAt = null,
      refreshTokenExpiresAt = null
    )
    dao.setTokensOfScope(accountId, tokens, Global).shouldBeOk()
    dao.getTokensOfScope(accountId, Global).shouldBeOk(tokens)
  }

  test("setTokens does not overwrite expiry if new expiry is null") {
    val globalTokens = AccountAuthTokens(
      accessToken = AccessToken("access-token-global"),
      refreshToken = RefreshToken("refresh-token-global"),
      accessTokenExpiresAt = now,
      refreshTokenExpiresAt = now.plus(30.days)
    )
    dao.setTokensOfScope(accountId, globalTokens, Global).shouldBeOk()

    val tokens = AccountAuthTokens(
      accessToken = AccessToken("access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = now,
      // Refresh token expiry is only updated on a new authentication, not on access token
      // refresh, so we must account for a nullable value
      refreshTokenExpiresAt = null
    )
    dao.setTokensOfScope(accountId, tokens, Global).shouldBeOk()
    dao.getTokensOfScope(accountId, Global).shouldBeOk(
      AccountAuthTokens(
        accessToken = AccessToken("access-token"),
        refreshToken = RefreshToken("refresh-token"),
        accessTokenExpiresAt = now,
        refreshTokenExpiresAt = now.plus(30.days)
      )
    )
  }

  test("handles multiple scopes") {
    val globalTokens = AccountAuthTokens(
      accessToken = AccessToken("access-token-global"),
      refreshToken = RefreshToken("refresh-token-global"),
      accessTokenExpiresAt = now,
      refreshTokenExpiresAt = now.plus(30.days)
    )
    dao.setTokensOfScope(accountId, globalTokens, Global).shouldBeOk()

    val recoveryTokens = AccountAuthTokens(
      accessToken = AccessToken("access-token-recovery"),
      refreshToken = RefreshToken("refresh-token-recovery"),
      accessTokenExpiresAt = now,
      refreshTokenExpiresAt = now.plus(30.days)
    )
    dao.setTokensOfScope(accountId, recoveryTokens, Recovery).shouldBeOk()

    dao.getTokensOfScope(accountId, Global).shouldBeOk(globalTokens)
    dao.getTokensOfScope(accountId, Recovery).shouldBeOk(recoveryTokens)
  }

  test("handles invalid expiry timestamps") {
    // Store invalid expiry timestamps directly in the store
    val store = encryptedKeyValueStoreFactory.getOrCreate("AuthTokenStore")
    store.putString("accessToken_global_test-account", "access-token")
    store.putString("refreshToken_global_test-account", "refresh-token")
    store.putString("accessToken_expiresAt_global_test-account", "invalid-timestamp")
    store.putString("refreshToken_expiresAt_global_test-account", "invalid-timestamp")

    val tokens = dao.getTokensOfScope(accountId, Global).shouldBeOk()!!
    tokens.accessToken.raw shouldBe "access-token"
    tokens.refreshToken.raw shouldBe "refresh-token"
    tokens.accessTokenExpiresAt.shouldBeNull()
    tokens.refreshTokenExpiresAt.shouldBeNull()
  }

  test("clear removes all tokens") {
    val tokens = AccountAuthTokens(
      accessToken = AccessToken("access-token"),
      refreshToken = RefreshToken("refresh-token"),
      accessTokenExpiresAt = now,
      refreshTokenExpiresAt = now.plus(30.days)
    )
    dao.setTokensOfScope(accountId, tokens, Global).shouldBeOk()
    dao.clear().shouldBeOk()
    dao.getTokensOfScope(accountId, Global).shouldBeOk(null)
  }
})
