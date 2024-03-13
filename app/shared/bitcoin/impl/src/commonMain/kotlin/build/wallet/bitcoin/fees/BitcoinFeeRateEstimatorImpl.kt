package build.wallet.bitcoin.fees

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.bdk.BdkBlockchainProvider
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.SIXTY_MINUTES
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.THIRTY_MINUTES
import build.wallet.bitcoin.transactions.targetBlocks
import build.wallet.ktor.result.NetworkingError
import build.wallet.ktor.result.bodyResult
import build.wallet.logging.logNetworkFailure
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

class BitcoinFeeRateEstimatorImpl(
  private val mempoolHttpClient: MempoolHttpClient,
  private val bdkBlockchainProvider: BdkBlockchainProvider,
) : BitcoinFeeRateEstimator {
  override suspend fun estimatedFeeRateForTransaction(
    networkType: BitcoinNetworkType,
    estimatedTransactionPriority: EstimatedTransactionPriority,
  ): FeeRate {
    return mempoolHttpClient.client(networkType)
      .bodyResult<Response> { get("api/v1/fees/recommended") }
      .map { body ->
        when (estimatedTransactionPriority) {
          FASTEST -> FeeRate(body.fastestFee)
          THIRTY_MINUTES -> FeeRate(body.halfHourFee)
          SIXTY_MINUTES -> FeeRate(body.hourFee)
        }
      }
      .logNetworkFailure { "Failed to get fee rate" }
      .getOrElse {
        // Attempt to use Electrum estimates if there is an issue fetching from Mempool.
        // If we get an error trying to interact with Electrum, return the fallback feerate, which
        // is 1sat/vB
        bdkBlockchainProvider.blockchain().result
          .fold(
            success = { blockchain ->
              blockchain.estimateFee(estimatedTransactionPriority.targetBlocks())
                .result
                .fold(
                  success = { FeeRate(it) },
                  failure = { FeeRate.Fallback }
                )
            },
            failure = { FeeRate.Fallback }
          )
      }
  }

  override suspend fun getEstimatedFeeRates(
    networkType: BitcoinNetworkType,
  ): Result<Map<EstimatedTransactionPriority, FeeRate>, NetworkingError> {
    return mempoolHttpClient.client(networkType)
      .bodyResult<Response> { get("api/v1/fees/recommended") }
      .map { response ->
        Ok(
          mapOf(
            FASTEST to FeeRate(response.fastestFee),
            THIRTY_MINUTES to FeeRate(response.halfHourFee),
            SIXTY_MINUTES to FeeRate(response.hourFee)
          )
        )
      }
      .logNetworkFailure { "Failed to get fee rate" }
      .getOrElse { networkingError ->
        val fallbackRate = FeeRate.Fallback
        val feeRateMap = mutableMapOf<EstimatedTransactionPriority, FeeRate>()

        bdkBlockchainProvider.blockchain().result.map { blockchain ->
          EstimatedTransactionPriority.values().forEach { priority ->
            blockchain.estimateFee(priority.targetBlocks())
              .result
              .fold(
                success = { feeRate -> feeRateMap[priority] = FeeRate(feeRate) },
                failure = { feeRateMap[priority] = fallbackRate }
              )
          }
        }.getOrElse { networkingError }

        Ok(feeRateMap)
      }
  }

  /** Represents the response from mempool */
  @Serializable
  private data class Response(
    val fastestFee: Float,
    val halfHourFee: Float,
    val hourFee: Float,
    val economyFee: Float,
    val minimumFee: Float,
  )
}
