package build.wallet.bitcoin.fees

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class AugurFeesHttpClientMock : AugurFeesHttpClient {
  var error: Error? = null

  override suspend fun getAugurFeesFeeRate(
    networkType: BitcoinNetworkType,
    estimatedTransactionPriority: EstimatedTransactionPriority,
  ): Result<FeeRate, Error> {
    if (error != null) {
      return Err(error!!)
    }

    return Ok(
      when (estimatedTransactionPriority) {
        FASTEST -> FeeRate(10f)
        THIRTY_MINUTES -> FeeRate(5f)
        SIXTY_MINUTES -> FeeRate(3f)
      }
    )
  }

  override suspend fun getAugurFeesFeeRates(
    networkType: BitcoinNetworkType,
  ): Result<FeeRatesByPriority, Error> {
    if (error != null) {
      return Err(error!!)
    }

    return Ok(
      FeeRatesByPriority(
        fastestFeeRate = FeeRate(10f),
        halfHourFeeRate = FeeRate(5f),
        hourFeeRate = FeeRate(3f)
      )
    )
  }

  fun reset() {
    error = null
  }
}
