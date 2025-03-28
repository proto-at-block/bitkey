package build.wallet.bitcoin.fees

import build.wallet.bdk.bindings.estimateFee
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.bdk.BdkBlockchainProvider
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import build.wallet.bitcoin.transactions.targetBlocks
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ktor.result.RedactedResponseBody
import build.wallet.ktor.result.bodyResult
import build.wallet.logging.logNetworkFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import dev.zacsweers.redacted.annotations.Unredacted
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

@BitkeyInject(AppScope::class)
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
  ): Result<FeeRatesByPriority, Error> =
    coroutineBinding {
      mempoolHttpClient.client(networkType)
        .bodyResult<Response> { get("api/v1/fees/recommended") }
        .logNetworkFailure { "Failed to get fee rates" }
        .fold(
          success = {
            FeeRatesByPriority(
              fastestFeeRate = FeeRate(it.fastestFee),
              halfHourFeeRate = FeeRate(it.halfHourFee),
              hourFeeRate = FeeRate(it.hourFee)
            )
          },
          failure = {
            val feeRateMap = mutableMapOf<EstimatedTransactionPriority, FeeRate>()
            val blockchain = bdkBlockchainProvider.blockchain()
              .result
              .bind()

            EstimatedTransactionPriority.entries
              .scan(mutableMapOf<EstimatedTransactionPriority, FeeRate>()) { feeRates, priority ->
                blockchain.estimateFee(priority.targetBlocks())
                  .result
                  .fold(
                    success = { feeRate -> feeRates[priority] = FeeRate(feeRate) },
                    failure = { feeRates[priority] = FeeRate.Fallback }
                  )
                feeRates
              }

            FeeRatesByPriority(
              fastestFeeRate = feeRateMap[FASTEST] ?: FeeRate.Fallback,
              halfHourFeeRate = feeRateMap[THIRTY_MINUTES] ?: FeeRate.Fallback,
              hourFeeRate = feeRateMap[SIXTY_MINUTES] ?: FeeRate.Fallback
            )
          }
        )
    }

  /** Represents the response from mempool */
  @Unredacted
  @Serializable
  private data class Response(
    val fastestFee: Float,
    val halfHourFee: Float,
    val hourFee: Float,
    val economyFee: Float,
    val minimumFee: Float,
  ) : RedactedResponseBody
}
