package build.wallet.memfault

import build.wallet.availability.NetworkConnection
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.availability.networkReachabilityPlugin
import build.wallet.ktor.result.client.KtorLogLevelPolicy
import build.wallet.ktor.result.client.installLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.appendIfNameAbsent
import kotlinx.serialization.json.Json

class MemfaultHttpClientImpl(
  val logLevelPolicy: KtorLogLevelPolicy,
  private val networkReachabilityProvider: NetworkReachabilityProvider,
) : MemfaultHttpClient {
  override fun client(): HttpClient =
    HttpClient {
      installLogging(
        tag = "Memfault",
        logLevel = logLevelPolicy.level()
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
