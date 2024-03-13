package build.wallet.bitcoin.fees

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimator.FeeEstimationError
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.SIXTY_MINUTES
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.THIRTY_MINUTES
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.ionspin.kotlin.bignum.decimal.BigDecimal

class BitcoinTransactionFeeEstimatorMock(
  var feesResult: Result<Map<EstimatedTransactionPriority, Fee>, FeeEstimationError> =
    Ok(
      mapOf(
        FASTEST to Fee(BitcoinMoney.btc(BigDecimal.TEN), oneSatPerVbyteFeeRate),
        THIRTY_MINUTES to Fee(BitcoinMoney.btc(BigDecimal.TWO), oneSatPerVbyteFeeRate),
        SIXTY_MINUTES to Fee(BitcoinMoney.btc(BigDecimal.ONE), oneSatPerVbyteFeeRate)
      )
    ),
) : BitcoinTransactionFeeEstimator {
  override suspend fun getFeesForTransaction(
    priorities: List<EstimatedTransactionPriority>,
    keyset: SpendingKeyset,
    fullAccountConfig: FullAccountConfig,
    recipientAddress: BitcoinAddress,
    amount: BitcoinTransactionSendAmount,
  ): Result<Map<EstimatedTransactionPriority, Fee>, FeeEstimationError> {
    return feesResult
  }
}
