package build.wallet.bitcoin.fees

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class BitcoinFeeRateEstimatorMock : BitcoinFeeRateEstimator {
  override suspend fun estimatedFeeRateForTransaction(
    networkType: BitcoinNetworkType,
    estimatedTransactionPriority: EstimatedTransactionPriority,
  ): FeeRate {
    return FeeRate.Fallback
  }

  var getEstimatedFeeRateResult: Result<FeeRatesByPriority, NetworkingError> =
    Ok(
      FeeRatesByPriority(
        fastestFeeRate = FeeRate(3.0f),
        halfHourFeeRate = FeeRate(2.0f),
        hourFeeRate = FeeRate(1.0f)
      )
    )

  override suspend fun getEstimatedFeeRates(networkType: BitcoinNetworkType) =
    getEstimatedFeeRateResult
}
