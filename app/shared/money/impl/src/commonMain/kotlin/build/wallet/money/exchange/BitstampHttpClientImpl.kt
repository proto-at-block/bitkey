package build.wallet.money.exchange

import build.wallet.availability.NetworkConnection
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.availability.networkReachabilityPlugin
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType.Application
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class BitstampHttpClientImpl(
  private val networkReachabilityProvider: NetworkReachabilityProvider,
) : BitstampHttpClient {
  override fun client(): HttpClient = client

  private val client by lazy {
    HttpClient {
      install(ContentNegotiation) {
        json(
          Json {
            ignoreUnknownKeys = true
          }
        )
      }

      defaultRequest {
        accept(Application.Json)
        contentType(Application.Json)
        url("https://www.bitstamp.net/")
      }

      HttpResponseValidator {
        networkReachabilityPlugin(
          connection = NetworkConnection.HttpClientNetworkConnection.Bitstamp,
          networkReachabilityProvider = networkReachabilityProvider
        )
      }
    }
  }
}
