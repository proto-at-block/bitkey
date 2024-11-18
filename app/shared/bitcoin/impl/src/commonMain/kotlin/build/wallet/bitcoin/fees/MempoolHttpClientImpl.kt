package build.wallet.bitcoin.fees

import build.wallet.availability.NetworkConnection
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.availability.networkReachabilityPlugin
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.*
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
import kotlinx.serialization.json.Json

class MempoolHttpClientImpl(
  private val appVariant: AppVariant,
  private val networkReachabilityProvider: NetworkReachabilityProvider,
) : MempoolHttpClient {
  override fun client(networkType: BitcoinNetworkType): HttpClient =
    HttpClient {
      installLogging(
        tag = "Mempool",
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
        contentType(Application.Json)

        url(
          when (networkType) {
            BITCOIN -> "https://bitkey.mempool.space/"
            // Use signet for lower rates; this can be important so tests don't get priced out.
            REGTEST,
            TESTNET,
            SIGNET,
            -> "https://bitkey.mempool.space/signet/"
          }
        )
      }

      HttpResponseValidator {
        networkReachabilityPlugin(
          connection = NetworkConnection.HttpClientNetworkConnection.Mempool,
          networkReachabilityProvider = networkReachabilityProvider
        )
      }
    }
}
