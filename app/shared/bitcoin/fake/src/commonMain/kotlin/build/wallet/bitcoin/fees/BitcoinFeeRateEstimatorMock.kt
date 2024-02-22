package build.wallet.bitcoin.fees

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.SIXTY_MINUTES
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.THIRTY_MINUTES
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

  var getEstimatedFeeRateResult: Result<Map<EstimatedTransactionPriority, FeeRate>, NetworkingError> =
    Ok(
      mapOf(
        FASTEST to FeeRate(satsPerVByte = 3f),
        THIRTY_MINUTES to FeeRate(satsPerVByte = 2f),
        SIXTY_MINUTES to FeeRate(satsPerVByte = 1f)
      )
    )

  override suspend fun getEstimatedFeeRates(networkType: BitcoinNetworkType) =
    getEstimatedFeeRateResult
}
