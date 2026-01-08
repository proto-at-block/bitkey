package build.wallet.bitcoin.fees

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class MempoolHttpClientMock : MempoolHttpClient {
  var error: Error? = null

  override suspend fun getMempoolFeeRate(
    networkType: BitcoinNetworkType,
    estimatedTransactionPriority: EstimatedTransactionPriority,
  ): Result<FeeRate, Error> {
    if (error != null) {
      return Err(error!!)
    }

    return Ok(
      when (estimatedTransactionPriority) {
        FASTEST -> FeeRate(5.0f)
        THIRTY_MINUTES -> FeeRate(3.0f)
        SIXTY_MINUTES -> FeeRate(2.0f)
      }
    )
  }

  override suspend fun getMempoolFeeRates(
    networkType: BitcoinNetworkType,
  ): Result<FeeRatesByPriority, Error> {
    if (error != null) {
      return Err(error!!)
    }

    return Ok(
      FeeRatesByPriority(
        fastestFeeRate = FeeRate(5.0f),
        halfHourFeeRate = FeeRate(3.0f),
        hourFeeRate = FeeRate(2.0f)
      )
    )
  }

  fun reset() {
    error = null
  }
}
