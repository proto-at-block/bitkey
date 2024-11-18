package build.wallet.memfault

import build.wallet.availability.NetworkConnection
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.availability.networkReachabilityPlugin
import build.wallet.ktor.result.client.installLogging
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Development
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.serialization.json.Json

class MemfaultHttpClientImpl(
  private val appVariant: AppVariant,
  private val networkReachabilityProvider: NetworkReachabilityProvider,
) : MemfaultHttpClient {
  override fun client(): HttpClient =
    HttpClient {
      installLogging(
        tag = "Memfault",
        logLevel = when (appVariant) {
          Development -> LogLevel.ALL
          else -> LogLevel.INFO
        }
      )

      install(ContentNegotiation) {
        json(
          Json {
            ignoreUnknownKeys = true
          }
        )
      }

      defaultRequest {
        accept(Application.Json)
        headers.appendIfNameAbsent(HttpHeaders.ContentType, Application.Json.toString())
      }

      HttpResponseValidator {
        networkReachabilityPlugin(
          connection = NetworkConnection.HttpClientNetworkConnection.Memfault,
          networkReachabilityProvider = networkReachabilityProvider
        )
      }
    }
}
