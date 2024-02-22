package build.wallet.bitcoin.fees

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.money.Money
import com.github.michaelbull.result.Result

/**
 * Estimates the potential fee for a transaction given the criteria/priorities of the transaction
 */
interface BitcoinTransactionFeeEstimator {
  /**
   * Get fees for transaction as [Money] with BTC as currency
   *
   * @param priorities - the potential priorities for the transaction
   * @param keyset - the users spending keyset
   * @param recipientAddress - the potential recipient of the transaction
   * @param amount - the potential amount of the transaction
   * @return a map of  the estimated fees with the key being a given priority and the value [Money].
   * In the event a fee can't be found, it will be omitted from the map. For any other errors, this
   * will return an empty map
   */
  suspend fun getFeesForTransaction(
    priorities: List<EstimatedTransactionPriority>,
    keyset: SpendingKeyset,
    keyboxConfig: KeyboxConfig,
    recipientAddress: BitcoinAddress,
    amount: BitcoinTransactionSendAmount,
  ): Result<Map<EstimatedTransactionPriority, Fee>, FeeEstimationError>

  /**
   * Indicates an error during the process of loading fee information
   */
  sealed class FeeEstimationError : Error() {
    /**
     * There was an error getting the estimated fees
     */
    data class CannotGetFeesError(
      val isConnectivityError: Boolean,
    ) : FeeEstimationError()

    /**
     * There was an error getting the SpendingWallet
     */
    data object CannotGetSpendingWalletError : FeeEstimationError()

    /**
     * The user is attempting to construct a PSBT where they do not have all the outputs for (including fees).
     */
    data object InsufficientFundsError : FeeEstimationError()

    /**
     * The user is attempting to spend below the dust limit.
     */
    data object SpendingBelowDustLimitError : FeeEstimationError()

    /**
     * A generic error constructing the PSBT
     */
    data class CannotCreatePsbtError(
      override val message: String? = null,
    ) : FeeEstimationError()
  }
}
