package build.wallet.money.exchange

import io.ktor.client.HttpClient

/**
 * Provides [HttpClient] for Bitstamp API.
 */
interface BitstampHttpClient {
  fun client(): HttpClient
}
