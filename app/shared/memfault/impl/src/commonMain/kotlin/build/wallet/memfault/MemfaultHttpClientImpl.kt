package build.wallet.memfault

import build.wallet.availability.NetworkConnection
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.client.plugins.networkReachabilityPlugin
import build.wallet.ktor.result.client.installLogging
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

@BitkeyInject(AppScope::class)
class MemfaultHttpClientImpl(
  private val networkReachabilityProvider: NetworkReachabilityProvider,
) : MemfaultHttpClient {
  override fun client(): HttpClient =
    HttpClient {
      installLogging(
        tag = "Memfault",
        logLevel = LogLevel.BODY
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
