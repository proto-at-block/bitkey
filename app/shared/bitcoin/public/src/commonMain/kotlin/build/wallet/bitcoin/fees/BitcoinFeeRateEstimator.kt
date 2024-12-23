package build.wallet.bitcoin.fees

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import com.github.michaelbull.result.Result

interface BitcoinFeeRateEstimator {
  /**
   * Given a selected estimated time for the transaction to complete, a fee
   *
   * @param estimatedTransactionPriority - the estimated transaction time for the transaction to be
   * completed
   * @return the fee rate as a [FeeRate]
   */
  suspend fun estimatedFeeRateForTransaction(
    networkType: BitcoinNetworkType,
    estimatedTransactionPriority: EstimatedTransactionPriority,
  ): FeeRate

  /**
   * Get estimated fee rates for all transaction priorities
   *
   * @return An object of fee rates where the param is the [EstimatedTransactionPriority] and the value
   * is the fee rate as a [FeeRate].
   */
  suspend fun getEstimatedFeeRates(
    networkType: BitcoinNetworkType,
  ): Result<FeeRatesByPriority, Error>
}

class FeeRatesByPriority(
  val fastestFeeRate: FeeRate,
  val halfHourFeeRate: FeeRate,
  val hourFeeRate: FeeRate,
)
