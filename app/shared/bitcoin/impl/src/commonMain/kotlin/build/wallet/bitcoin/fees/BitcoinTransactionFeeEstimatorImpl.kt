package build.wallet.bitcoin.fees

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkError.InsufficientFunds
import build.wallet.bdk.bindings.BdkError.OutputBelowDustLimit
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimator.FeeEstimationError
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimator.FeeEstimationError.CannotCreatePsbtError
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimator.FeeEstimationError.CannotGetSpendingWalletError
import build.wallet.bitcoin.fees.BitcoinTransactionFeeEstimator.FeeEstimationError.SpendingBelowDustLimitError
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitkey.account.FullAccount
import build.wallet.datadog.DatadogRumMonitor
import build.wallet.datadog.ErrorSource.Source
import build.wallet.keybox.wallet.AppSpendingWalletProvider
import build.wallet.ktor.result.HttpError
import build.wallet.logging.logFailure
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.onFailure
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlin.math.ceil

// size of the 2-of-3 p2wsh witness
private const val WITNESS_SIZE_BYTES = 253

class BitcoinTransactionFeeEstimatorImpl(
  private val bitcoinFeeRateEstimator: BitcoinFeeRateEstimator,
  private val appSpendingWalletProvider: AppSpendingWalletProvider,
  private val datadogRumMonitor: DatadogRumMonitor,
) : BitcoinTransactionFeeEstimator {
  override suspend fun getFeesForTransaction(
    priorities: List<EstimatedTransactionPriority>,
    account: FullAccount,
    recipientAddress: BitcoinAddress,
    amount: BitcoinTransactionSendAmount,
  ): Result<Map<EstimatedTransactionPriority, Fee>, FeeEstimationError> {
    return binding {
      // Fetch the fee rates for transaction priorities and use them to create transactions for each
      // of the priorities passed in the function
      val feeRates =
        bitcoinFeeRateEstimator.getEstimatedFeeRates(account.config.bitcoinNetworkType)
          .mapError {
            FeeEstimationError.CannotGetFeesError(
              isConnectivityError = it is HttpError.NetworkError
            )
          }
          .bind()

      val wallet =
        appSpendingWalletProvider.getSpendingWallet(account)
          .logFailure { "Error creating spending wallet to get fees for a transaction" }
          .mapError { CannotGetSpendingWalletError }
          .bind()

      val psbt =
        wallet
          .createPsbt(
            recipientAddress = recipientAddress,
            amount = amount,
            feePolicy = FeePolicy.MinRelayRate
          )
          .logFailure { "Error creating psbt with tracking keyset" }
          .onFailure {
            datadogRumMonitor.addError(
              message = "Cannot create PSBT: ${it.message}",
              source = Source,
              attributes = emptyMap()
            )
          }
          .mapError {
            when (it is BdkError) {
              true -> {
                when (it) {
                  is OutputBelowDustLimit -> SpendingBelowDustLimitError
                  is InsufficientFunds -> FeeEstimationError.InsufficientFundsError
                  else -> CannotCreatePsbtError(it.message)
                }
              }
              else -> CannotCreatePsbtError(it.message)
            }
          }
          .bind()

      // calculate the vsize of the transaction *after* witnesses have signed the transaction
      // follows the formula outlined here:
      // https://bitcoin.stackexchange.com/questions/87275/how-to-calculate-segwit-transaction-fee-in-bytes
      val totalSize = (psbt.numOfInputs * WITNESS_SIZE_BYTES) + psbt.baseSize
      val weight = (3 * psbt.baseSize) + totalSize
      val vsize = ceil(weight / 4.0)

      // Return the map
      priorities.associateWith { priority ->
        val feeRate = feeRates[priority]

        feeRate?.let { rate ->
          val feeAmount = (rate.satsPerVByte * vsize).toInt().toBigInteger()
          Fee(amount = BitcoinMoney.sats(feeAmount), feeRate = feeRate)
        }
      }.filterNotNull()
    }
  }
}

private fun Map<EstimatedTransactionPriority, Fee?>.filterNotNull():
  Map<EstimatedTransactionPriority, Fee> =
  this.mapNotNull { entry ->
    entry.value?.let { value ->
      entry.key to value
    }
  }.toMap()
