package build.wallet.memfault

import io.ktor.client.HttpClient

/**
 * Provides [HttpClient] for memfault services.
 */
interface MemfaultHttpClient {
  fun client(): HttpClient
}
