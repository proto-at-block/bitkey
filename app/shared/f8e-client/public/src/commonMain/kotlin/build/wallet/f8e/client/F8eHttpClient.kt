package build.wallet.f8e.client

import build.wallet.crypto.WsmVerifier
import io.ktor.client.HttpClient

/**
 * Provides [HttpClient] for F8e APIs.
 */
interface F8eHttpClient : UnauthenticatedF8eHttpClient, AuthenticatedF8eHttpClient {
  val wsmVerifier: WsmVerifier
}
