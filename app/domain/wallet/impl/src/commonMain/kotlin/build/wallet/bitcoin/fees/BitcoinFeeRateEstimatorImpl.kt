package build.wallet.bitcoin.fees

import build.wallet.bdk.bindings.estimateFee
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.bdk.BdkBlockchainProvider
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import build.wallet.bitcoin.transactions.targetBlocks
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.AugurFeeComparisonLoggingFeatureFlag
import build.wallet.feature.flags.AugurFeesEstimationFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.logging.logInfo
import build.wallet.logging.logWarn
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onSuccess

@BitkeyInject(AppScope::class)
class BitcoinFeeRateEstimatorImpl(
  private val mempoolHttpClient: MempoolHttpClient,
  private val augurFeesHttpClient: AugurFeesHttpClient,
  private val bdkBlockchainProvider: BdkBlockchainProvider,
  private val augurFeesEstimationFeatureFlag: AugurFeesEstimationFeatureFlag,
  private val augurFeeComparisonLoggingFeatureFlag: AugurFeeComparisonLoggingFeatureFlag,
) : BitcoinFeeRateEstimator {
  override suspend fun estimatedFeeRateForTransaction(
    networkType: BitcoinNetworkType,
    estimatedTransactionPriority: EstimatedTransactionPriority,
  ): FeeRate {
    val useAugur = augurFeesEstimationFeatureFlag.isEnabled()
    val logComparison = augurFeeComparisonLoggingFeatureFlag.isEnabled()

    // Fetch Augur if it's the primary source OR if we need it for comparison logging
    val augurRate = if (useAugur || logComparison) {
      augurFeesHttpClient.getAugurFeesFeeRate(networkType, estimatedTransactionPriority)
    } else {
      null
    }

    // Fetch Mempool if it's the primary source OR if we need it for comparison logging OR as fallback for Augur
    val mempoolRate = if (!useAugur || logComparison || augurRate?.isErr == true) {
      mempoolHttpClient.getMempoolFeeRate(networkType, estimatedTransactionPriority)
    } else {
      null
    }

    // Log comparison if enabled and both results are available
    if (logComparison) {
      augurRate?.onSuccess { augur ->
        mempoolRate?.onSuccess { mempool ->
          val comparison = FeeRateComparison.compare(augur, mempool)
          logInfo { "Augur vs Mempool fee comparison for $estimatedTransactionPriority: $comparison" }
        }
      }
    }

    // Return the appropriate rate based on the primary source and fallback chain
    return if (useAugur) {
      augurRate?.get()
        ?: mempoolRate?.get()
        ?: getBdkFeeRate(estimatedTransactionPriority)
    } else {
      mempoolRate?.get() ?: getBdkFeeRate(estimatedTransactionPriority)
    }
  }

  override suspend fun getEstimatedFeeRates(
    networkType: BitcoinNetworkType,
  ): Result<FeeRatesByPriority, Error> =
    coroutineBinding {
      val useAugur = augurFeesEstimationFeatureFlag.isEnabled()
      val logComparison = augurFeeComparisonLoggingFeatureFlag.isEnabled()

      // Fetch Augur if it's the primary source OR if we need it for comparison logging
      val augurRates = if (useAugur || logComparison) {
        augurFeesHttpClient.getAugurFeesFeeRates(networkType)
      } else {
        null
      }

      // Fetch Mempool if it's the primary source OR if we need it for comparison logging OR as fallback for Augur
      val mempoolRates = if (!useAugur || logComparison || augurRates?.isErr == true) {
        mempoolHttpClient.getMempoolFeeRates(networkType)
      } else {
        null
      }

      // Log comparison if enabled and both results are available
      if (logComparison) {
        augurRates?.onSuccess { augur ->
          mempoolRates?.onSuccess { mempool ->
            val fastestComparison =
              FeeRateComparison.compare(augur.fastestFeeRate, mempool.fastestFeeRate)
            val halfHourComparison =
              FeeRateComparison.compare(augur.halfHourFeeRate, mempool.halfHourFeeRate)
            val hourComparison = FeeRateComparison.compare(augur.hourFeeRate, mempool.hourFeeRate)

            logInfo {
              "Augur vs Mempool fee comparison: " +
                "FASTEST=$fastestComparison, " +
                "THIRTY_MINUTES=$halfHourComparison, " +
                "SIXTY_MINUTES=$hourComparison"
            }
          }
        }
      }

      // Return the appropriate rates based on the primary source and fallback chain
      if (useAugur) {
        augurRates?.get()
          ?: mempoolRates?.get()
          ?: getBdkFeeRates().bind()
      } else {
        mempoolRates?.get() ?: getBdkFeeRates().bind()
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
