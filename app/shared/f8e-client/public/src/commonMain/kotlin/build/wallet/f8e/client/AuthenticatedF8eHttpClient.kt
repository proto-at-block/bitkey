package build.wallet.f8e.client

import io.ktor.client.HttpClient

interface AuthenticatedF8eHttpClient {
  /**
   * Client that talks to F8e with authentication.
   */
  suspend fun authenticated(): HttpClient
}
