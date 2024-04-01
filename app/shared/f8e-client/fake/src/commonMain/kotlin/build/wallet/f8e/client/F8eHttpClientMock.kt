package build.wallet.f8e.client

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.app.AppAuthKey
import build.wallet.bitkey.f8e.AccountId
import build.wallet.crypto.PublicKey
import build.wallet.crypto.WsmVerifier
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine

class F8eHttpClientMock(override val wsmVerifier: WsmVerifier) : F8eHttpClient {
  override suspend fun authenticated(
    f8eEnvironment: F8eEnvironment,
    accountId: AccountId,
    appFactorProofOfPossessionAuthKey: PublicKey<out AppAuthKey>?,
    hwFactorProofOfPossession: HwFactorProofOfPossession?,
    engine: HttpClientEngine?,
    authTokenScope: AuthTokenScope,
  ) = HttpClient()

  override suspend fun unauthenticated(
    f8eEnvironment: F8eEnvironment,
    engine: HttpClientEngine?,
  ) = HttpClient()
}
