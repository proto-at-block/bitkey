package build.wallet.f8e.client

import io.ktor.client.HttpClient

interface UnauthenticatedF8eHttpClient {
  /**
   * Client that talks to F8e without any authentication.
   */
  suspend fun unauthenticated(): HttpClient
}
