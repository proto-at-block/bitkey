package build.wallet.f8e.client

import build.wallet.crypto.WsmVerifier
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine

class F8eHttpClientMock(
  override val wsmVerifier: WsmVerifier,
  private val engine: HttpClientEngine? = null,
) : F8eHttpClient {
  override suspend fun authenticated() = HttpClient()

  override suspend fun unauthenticated() = engine?.let { HttpClient(it) } ?: HttpClient()
}
