package build.wallet.auth

import bitkey.auth.AuthTokenScope.Global
import bitkey.auth.AuthTokenScope.Recovery
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.f8e.LiteAccountIdMock
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class AuthTokensServiceFakeTests : FunSpec({
  test("no tokens") {
    val service = AuthTokensServiceFake()

    service.getTokens(FullAccountIdMock, Global).shouldBeOk(null)
  }

  test("set tokens for Global scope") {
    val service = AuthTokensServiceFake()

    service.setTokens(FullAccountIdMock, AccountAuthTokensMock, Global).shouldBeOk()

    service.getTokens(FullAccountIdMock, Global).shouldBeOk(AccountAuthTokensMock)
    service.getTokens(FullAccountIdMock, Recovery).shouldBeOk(null)
  }

  test("set tokens for Recovery scope") {
    val service = AuthTokensServiceFake()

    service.setTokens(FullAccountIdMock, AccountAuthTokensMock, Recovery).shouldBeOk()

    service.getTokens(FullAccountIdMock, Recovery).shouldBeOk(AccountAuthTokensMock)
    service.getTokens(FullAccountIdMock, Global).shouldBeOk(null)
  }

  test("set tokens for Global and Recovery scope") {
    val service = AuthTokensServiceFake()

    service.setTokens(FullAccountIdMock, AccountAuthTokensMock, Global).shouldBeOk()
    service.setTokens(FullAccountIdMock, AccountAuthTokensMock2, Recovery).shouldBeOk()

    service.getTokens(FullAccountIdMock, Global).shouldBeOk(AccountAuthTokensMock)
    service.getTokens(FullAccountIdMock, Recovery).shouldBeOk(AccountAuthTokensMock2)
  }

  test("set tokens for different accounts same scope") {
    val service = AuthTokensServiceFake()

    service.setTokens(FullAccountIdMock, AccountAuthTokensMock, Global).shouldBeOk()
    service.setTokens(LiteAccountIdMock, AccountAuthTokensMock2, Global).shouldBeOk()

    service.getTokens(FullAccountIdMock, Global).shouldBeOk(AccountAuthTokensMock)
    service.getTokens(FullAccountIdMock, Recovery).shouldBeOk(null)
    service.getTokens(LiteAccountIdMock, Global).shouldBeOk(AccountAuthTokensMock2)
    service.getTokens(LiteAccountIdMock, Recovery).shouldBeOk(null)
  }

  test("set tokens for different accounts different scopes") {
    val service = AuthTokensServiceFake()

    service.setTokens(FullAccountIdMock, AccountAuthTokensMock, Global).shouldBeOk()
    service.setTokens(LiteAccountIdMock, AccountAuthTokensMock2, Recovery).shouldBeOk()

    service.getTokens(FullAccountIdMock, Global).shouldBeOk(AccountAuthTokensMock)
    service.getTokens(FullAccountIdMock, Recovery).shouldBeOk(null)
    service.getTokens(LiteAccountIdMock, Recovery).shouldBeOk(AccountAuthTokensMock2)
    service.getTokens(LiteAccountIdMock, Global).shouldBeOk(null)
  }

  test("setting token returns fake error") {
    val service = AuthTokensServiceFake()

    val error = Error("foo")
    service.setTokensError = error

    service.setTokens(FullAccountIdMock, AccountAuthTokensMock, Global).shouldBeErr(error)
  }

  test("error refreshing access token when old tokens are not present") {
    val service = AuthTokensServiceFake()

    service.refreshAccessTokenWithApp(Production, FullAccountIdMock, Global)
      .shouldBeErr(Error("No Global tokens found for $FullAccountIdMock"))

    service.refreshAccessTokenWithApp(Production, LiteAccountIdMock, Recovery)
      .shouldBeErr(Error("No Recovery tokens found for $LiteAccountIdMock"))
  }

  test("refresh existing access token") {
    val service = AuthTokensServiceFake()

    service.setTokens(FullAccountIdMock, AccountAuthTokensMock, Global).shouldBeOk()
    val oldTokens = service.getTokens(FullAccountIdMock, Global).shouldBeOk().shouldNotBeNull()

    val refreshedTokens1 =
      service.refreshAccessTokenWithApp(Production, FullAccountIdMock, Global).shouldBeOk()
    service.getTokens(FullAccountIdMock, Global).shouldBeOk(refreshedTokens1)

    oldTokens.accessToken.shouldNotBe(refreshedTokens1.accessToken)
    oldTokens.refreshToken.shouldBe(refreshedTokens1.refreshToken)

    val refreshedTokens2 =
      service.refreshAccessTokenWithApp(Production, FullAccountIdMock, Global).shouldBeOk()
    service.getTokens(FullAccountIdMock, Global).shouldBeOk(refreshedTokens2)

    refreshedTokens1.accessToken.shouldNotBe(refreshedTokens2.accessToken)
    refreshedTokens1.refreshToken.shouldBe(refreshedTokens2.refreshToken)
  }

  test("refreshing access token returns fake error") {
    val service = AuthTokensServiceFake()
    val error = Error("foo")
    service.refreshAccessTokenError = error

    service.refreshAccessTokenWithApp(Production, FullAccountIdMock, Global).shouldBeErr(error)
  }

  test("reset service") {
    val service = AuthTokensServiceFake()

    service.setTokens(FullAccountIdMock, AccountAuthTokensMock, Global).shouldBeOk()
    service.setTokens(LiteAccountIdMock, AccountAuthTokensMock2, Recovery).shouldBeOk()

    service.getTokens(FullAccountIdMock, Global).shouldBeOk(AccountAuthTokensMock)
    service.getTokens(LiteAccountIdMock, Recovery).shouldBeOk(AccountAuthTokensMock2)

    service.refreshAccessTokenError = Error()
    service.setTokensError = Error()

    service.reset()

    service.getTokens(FullAccountIdMock, Global).shouldBeOk(null)
    service.getTokens(FullAccountIdMock, Recovery).shouldBeOk(null)
    service.getTokens(LiteAccountIdMock, Global).shouldBeOk(null)
    service.getTokens(LiteAccountIdMock, Recovery).shouldBeOk(null)
    service.refreshAccessTokenError.shouldBeNull()
    service.setTokensError.shouldBeNull()
  }

  test("clear tokens") {
    val service = AuthTokensServiceFake()
    service.setTokens(FullAccountIdMock, AccountAuthTokensMock, Global).shouldBeOk()
    service.setTokens(LiteAccountIdMock, AccountAuthTokensMock2, Recovery).shouldBeOk()

    service.getTokens(FullAccountIdMock, Global).shouldBeOk(AccountAuthTokensMock)
    service.getTokens(LiteAccountIdMock, Recovery).shouldBeOk(AccountAuthTokensMock2)

    service.reset()

    service.getTokens(FullAccountIdMock, Global).shouldBeOk(null)
    service.getTokens(FullAccountIdMock, Recovery).shouldBeOk(null)
    service.getTokens(LiteAccountIdMock, Global).shouldBeOk(null)
    service.getTokens(LiteAccountIdMock, Recovery).shouldBeOk(null)
  }
})
