package build.wallet.f8e.client

import build.wallet.f8e.F8eEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine

interface UnauthenticatedF8eHttpClient {
  /**
   * Client that talks to F8e without any authentication.
   */
  suspend fun unauthenticated(
    f8eEnvironment: F8eEnvironment,
    engine: HttpClientEngine? = null,
  ): HttpClient
}
