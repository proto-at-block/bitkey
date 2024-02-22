package build.wallet.bitcoin.fees

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.ktor.result.NetworkingError
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
   * @return A map of fee rates where the key is the [EstimatedTransactionPriority] and the value
   * is the fee rate as a [FeeRate].
   */
  suspend fun getEstimatedFeeRates(
    networkType: BitcoinNetworkType,
  ): Result<Map<EstimatedTransactionPriority, FeeRate>, NetworkingError>
}
