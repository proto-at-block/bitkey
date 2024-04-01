package build.wallet.bitcoin.fees

import build.wallet.availability.NetworkConnection
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.availability.networkReachabilityPlugin
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.BITCOIN
import build.wallet.bitcoin.BitcoinNetworkType.REGTEST
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.BitcoinNetworkType.TESTNET
import build.wallet.ktor.result.client.KtorLogLevelPolicy
import build.wallet.ktor.result.client.installLogging
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType.Application
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class MempoolHttpClientImpl(
  private val logLevelPolicy: KtorLogLevelPolicy,
  private val networkReachabilityProvider: NetworkReachabilityProvider,
) : MempoolHttpClient {
  override fun client(networkType: BitcoinNetworkType): HttpClient =
    HttpClient {
      installLogging(
        tag = "Mempool",
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
