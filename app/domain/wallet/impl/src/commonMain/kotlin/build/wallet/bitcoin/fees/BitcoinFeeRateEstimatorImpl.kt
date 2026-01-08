package build.wallet.bitcoin.fees

import build.wallet.bdk.bindings.estimateFee
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.bdk.BdkBlockchainProvider
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import build.wallet.bitcoin.transactions.targetBlocks
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.AugurFeesEstimationFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logWarn
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.getOrElse

@BitkeyInject(AppScope::class)
class BitcoinFeeRateEstimatorImpl(
  private val mempoolHttpClient: MempoolHttpClient,
  private val augurFeesHttpClient: AugurFeesHttpClient,
  private val bdkBlockchainProvider: BdkBlockchainProvider,
  private val augurFeesEstimationFeatureFlag: AugurFeesEstimationFeatureFlag,
) : BitcoinFeeRateEstimator {
  override suspend fun estimatedFeeRateForTransaction(
    networkType: BitcoinNetworkType,
    estimatedTransactionPriority: EstimatedTransactionPriority,
  ): FeeRate {
    return if (augurFeesEstimationFeatureFlag.isEnabled()) {
      augurFeesHttpClient.getAugurFeesFeeRate(networkType, estimatedTransactionPriority)
        .getOrElse {
          // Fallback to mempool if AugurFees fails
          mempoolHttpClient.getMempoolFeeRate(networkType, estimatedTransactionPriority)
            .getOrElse {
              // Final fallback to BDK/Electrum
              getBdkFeeRate(estimatedTransactionPriority)
            }
        }
    } else {
      mempoolHttpClient.getMempoolFeeRate(networkType, estimatedTransactionPriority)
        .getOrElse { mempoolError ->
          // Fallback to BDK/Electrum when mempool fails
          getBdkFeeRate(estimatedTransactionPriority)
        }
    }
  }

  override suspend fun getEstimatedFeeRates(
    networkType: BitcoinNetworkType,
  ): Result<FeeRatesByPriority, Error> =
    coroutineBinding {
      if (augurFeesEstimationFeatureFlag.isEnabled()) {
        augurFeesHttpClient.getAugurFeesFeeRates(networkType)
          .getOrElse {
            // Fallback to mempool if AugurFees fails
            mempoolHttpClient.getMempoolFeeRates(networkType)
              .getOrElse { mempoolError ->
                // Final fallback to BDK/Electrum
                getBdkFeeRates().bind()
              }
          }
      } else {
        mempoolHttpClient.getMempoolFeeRates(networkType)
          .getOrElse { mempoolError ->
            // Fallback to BDK/Electrum when mempool fails
            getBdkFeeRates().bind()
          }
      }
    }

  private suspend fun getBdkFeeRate(
    estimatedTransactionPriority: EstimatedTransactionPriority,
  ): FeeRate {
    return bdkBlockchainProvider.blockchain().result
      .fold(
        success = { blockchain ->
          blockchain.estimateFee(estimatedTransactionPriority.targetBlocks())
            .result
            .fold(
              success = { FeeRate(it) },
              failure = {
                logWarn { "BDK fee estimation failed for $estimatedTransactionPriority" }
                FeeRate.Fallback
              }
            )
        },
        failure = {
          logWarn { "BDK fee estimation failed for $estimatedTransactionPriority" }
          FeeRate.Fallback
        }
      )
  }

  private suspend fun getBdkFeeRates(): Result<FeeRatesByPriority, Error> =
    coroutineBinding {
      val blockchain = bdkBlockchainProvider.blockchain()
        .result
        .bind()

      val feeRateMap = mutableMapOf<EstimatedTransactionPriority, FeeRate>()

      EstimatedTransactionPriority.entries.forEach { priority ->
        blockchain.estimateFee(priority.targetBlocks())
          .result
          .fold(
            success = { feeRate -> feeRateMap[priority] = FeeRate(feeRate) },
            failure = {
              logWarn { "BDK fee estimation failed for $priority" }
              feeRateMap[priority] = FeeRate.Fallback
            }
          )
      }

      FeeRatesByPriority(
        fastestFeeRate = feeRateMap[FASTEST] ?: FeeRate.Fallback,
        halfHourFeeRate = feeRateMap[THIRTY_MINUTES] ?: FeeRate.Fallback,
        hourFeeRate = feeRateMap[SIXTY_MINUTES] ?: FeeRate.Fallback
      )
    }
}
