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
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import dev.zacsweers.redacted.annotations.Unredacted
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.ContentType.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(AppScope::class)
class AugurFeesHttpClientImpl(
  private val networkReachabilityProvider: NetworkReachabilityProvider,
  private val engine: HttpClientEngine? = null,
) : AugurFeesHttpClient {
  override suspend fun getAugurFeesFeeRate(
    networkType: BitcoinNetworkType,
    estimatedTransactionPriority: EstimatedTransactionPriority,
  ): Result<FeeRate, Error> =
    coroutineBinding {
      val body = client(networkType)
        .bodyResult<AugurFeesResponse> { get("fees") }
        .logNetworkFailure { "Failed to get AugurFees fee rate" }
        .bind()

      when (estimatedTransactionPriority) {
        // FASTEST: 3 blocks @ 95% confidence
        FASTEST -> {
          val feeRate = body.estimates.threeBlocks?.probabilities?.ninetyFivePercent?.feeRate
            ?: Err(Error("Missing required fee data for FASTEST priority")).bind()
          FeeRate(feeRate)
        }
        // THIRTY_MINUTES: 3 blocks @ 80% confidence
        THIRTY_MINUTES -> {
          val feeRate = body.estimates.threeBlocks?.probabilities?.eightyPercent?.feeRate
            ?: Err(Error("Missing required fee data for THIRTY_MINUTES priority")).bind()
          FeeRate(feeRate)
        }
        // SIXTY_MINUTES: 6 blocks @ 80% confidence
        SIXTY_MINUTES -> {
          val feeRate = body.estimates.sixBlocks?.probabilities?.eightyPercent?.feeRate
            ?: Err(Error("Missing required fee data for SIXTY_MINUTES priority")).bind()
          FeeRate(feeRate)
        }
      }
    }

  override suspend fun getAugurFeesFeeRates(
    networkType: BitcoinNetworkType,
  ): Result<FeeRatesByPriority, Error> =
    coroutineBinding {
      val body = client(networkType)
        .bodyResult<AugurFeesResponse> { get("fees") }
        .logNetworkFailure { "Failed to get AugurFees fee rates" }
        .bind()

      // Check for all required fee rates and fail if any are missing
      val fastestFeeRate = body.estimates.threeBlocks?.probabilities?.ninetyFivePercent?.feeRate
        ?: Err(Error("Missing fastest fee rate data from Augur API")).bind()
      val halfHourFeeRate = body.estimates.threeBlocks?.probabilities?.eightyPercent?.feeRate
        ?: Err(Error("Missing half-hour fee rate data from Augur API")).bind()
      val hourFeeRate = body.estimates.sixBlocks?.probabilities?.eightyPercent?.feeRate
        ?: Err(Error("Missing hour fee rate data from Augur API")).bind()

      FeeRatesByPriority(
        fastestFeeRate = FeeRate(fastestFeeRate),
        halfHourFeeRate = FeeRate(halfHourFeeRate),
        hourFeeRate = FeeRate(hourFeeRate)
      )
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
        tag = "Augur",
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
            BITCOIN,
            TESTNET,
            -> "https://pricing.bitcoin.block.xyz/"
            // Use staging for signet and regtest
            REGTEST,
            SIGNET,
            -> "https://pricing.bitcoin.blockstaging.xyz/"
          }
        )
      }

      HttpResponseValidator {
        networkReachabilityPlugin(
          connection = NetworkConnection.HttpClientNetworkConnection.AugurFees,
          networkReachabilityProvider = networkReachabilityProvider
        )
      }
    }
  }
}

@Unredacted
@Serializable
data class AugurFeesResponse(
  val estimates: AugurFeesEstimates,
  @SerialName("mempool_update_time") val mempoolUpdateTime: String,
) : RedactedResponseBody

@Serializable
data class AugurFeesEstimates(
  @SerialName("3") val threeBlocks: AugurFeesTimeEstimate? = null,
  @SerialName("6") val sixBlocks: AugurFeesTimeEstimate? = null,
  @SerialName("9") val nineBlocks: AugurFeesTimeEstimate? = null,
  @SerialName("12") val twelveBlocks: AugurFeesTimeEstimate? = null,
  @SerialName("18") val eighteenBlocks: AugurFeesTimeEstimate? = null,
  @SerialName("24") val twentyFourBlocks: AugurFeesTimeEstimate? = null,
  @SerialName("36") val thirtySixBlocks: AugurFeesTimeEstimate? = null,
  @SerialName("48") val fortyEightBlocks: AugurFeesTimeEstimate? = null,
  @SerialName("72") val seventyTwoBlocks: AugurFeesTimeEstimate? = null,
  @SerialName("96") val ninetySixBlocks: AugurFeesTimeEstimate? = null,
  @SerialName("144") val oneHundredFortyFourBlocks: AugurFeesTimeEstimate? = null,
)

@Serializable
data class AugurFeesTimeEstimate(
  val probabilities: AugurFeesProbabilities,
)

@Serializable
data class AugurFeesProbabilities(
  @SerialName("0.05") val fivePercent: AugurFeesFeeRate? = null,
  @SerialName("0.20") val twentyPercent: AugurFeesFeeRate? = null,
  @SerialName("0.50") val fiftyPercent: AugurFeesFeeRate? = null,
  @SerialName("0.80") val eightyPercent: AugurFeesFeeRate? = null,
  @SerialName("0.95") val ninetyFivePercent: AugurFeesFeeRate? = null,
)

@Serializable
data class AugurFeesFeeRate(
  @SerialName("fee_rate") val feeRate: Float,
)
