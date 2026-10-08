package build.wallet.auth

import bitkey.account.AccountConfigServiceFake
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.f8e.auth.AuthF8eClientMock
import build.wallet.ktor.result.HttpError
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AccountAuthenticatorImplTests : FunSpec({
  val authF8eClient = AuthF8eClientMock()
  val service = AccountAuthenticatorImpl(
    appAuthKeyMessageSigner = AppAuthKeyMessageSignerMock(),
    authF8eClient = authF8eClient,
    accountConfigService = AccountConfigServiceFake()
  )

  beforeTest {
    authF8eClient.reset()
  }

  test("hardware auth preserves connectivity error") {
    val connectivityError = HttpError.NetworkError(Error("offline"))
    authF8eClient.completeAuthenticationResult = Err(connectivityError)

    service.hwAuth(
      fullAccountId = FullAccountIdMock,
      session = "session",
      signature = "signature"
    ).shouldBeErrOfType<AuthNetworkError>().cause.shouldBe(connectivityError)
  }
})
