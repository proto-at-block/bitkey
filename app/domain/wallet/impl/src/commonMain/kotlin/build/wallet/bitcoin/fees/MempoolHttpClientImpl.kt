package build.wallet.bitcoin.fees

import build.wallet.availability.NetworkConnection
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.BitcoinNetworkType.*
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.client.plugins.networkReachabilityPlugin
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.ktor.result.client.installLogging
import build.wallet.logging.logNetworkFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import dev.zacsweers.redacted.annotations.Unredacted
import io.ktor.client.*
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
class MempoolHttpClientImpl(
  private val networkReachabilityProvider: NetworkReachabilityProvider,
  private val engine: HttpClientEngine? = null,
) : MempoolHttpClient {
  override suspend fun getMempoolFeeRate(
    networkType: BitcoinNetworkType,
    estimatedTransactionPriority: EstimatedTransactionPriority,
  ): Result<FeeRate, Error> {
    return client(networkType)
      .bodyResult<MempoolResponse> { get("api/v1/fees/recommended") }
      .map { body ->
        when (estimatedTransactionPriority) {
          FASTEST -> FeeRate(body.fastestFee)
          THIRTY_MINUTES -> FeeRate(body.halfHourFee)
          SIXTY_MINUTES -> FeeRate(body.hourFee)
        }
      }
      .logNetworkFailure { "Failed to get mempool fee rate" }
  }

  override suspend fun getMempoolFeeRates(
    networkType: BitcoinNetworkType,
  ): Result<FeeRatesByPriority, Error> {
    return client(networkType)
      .bodyResult<MempoolResponse> { get("api/v1/fees/recommended") }
      .map {
        FeeRatesByPriority(
          fastestFeeRate = FeeRate(it.fastestFee),
          halfHourFeeRate = FeeRate(it.halfHourFee),
          hourFeeRate = FeeRate(it.hourFee)
        )
      }
      .logNetworkFailure { "Failed to get mempool fee rates" }
  }

  private fun client(networkType: BitcoinNetworkType): HttpClient {
    return if (engine == null) {
      HttpClient { configureClient(this, networkType) }
    } else {
      HttpClient(engine) { configureClient(this, networkType) }
    }
  }

  private fun configureClient(
    config: HttpClientConfig<*>,
    networkType: BitcoinNetworkType,
  ) {
    with(config) {
      installLogging(
        tag = "Mempool",
        logLevel = LogLevel.BODY
      )

      install(ContentNegotiation) {
        json(
          Json {
            ignoreUnknownKeys = true
          }
        )
      }

      install(HttpTimeout) {
        connectTimeoutMillis = 15.seconds.inWholeMilliseconds
        requestTimeoutMillis = 60.seconds.inWholeMilliseconds
        socketTimeoutMillis = 60.seconds.inWholeMilliseconds
      }

      defaultRequest {
        accept(Application.Json)
        contentType(Application.Json)

        url(
          when (networkType) {
            BITCOIN -> "https://bitkey.mempool.space/"
            TESTNET -> "https://bitkey.mempool.space/testnet/"
            // Use signet for lower rates; this can be important so tests don't get priced out.
            REGTEST,
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

  @Unredacted
  @Serializable
  internal data class MempoolResponse(
    val fastestFee: Float,
    val halfHourFee: Float,
    val hourFee: Float,
    val economyFee: Float,
    val minimumFee: Float,
  ) : RedactedResponseBody
}
