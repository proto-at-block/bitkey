package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod.BumpFee
import build.wallet.bitkey.account.Account
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logError
import build.wallet.logging.logFailure
import build.wallet.logging.logWarn
import build.wallet.money.BitcoinMoney
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse

@BitkeyInject(AppScope::class)
class SpeedUpTransactionServiceImpl(
  private val feeRateEstimator: BitcoinFeeRateEstimator,
  private val bitcoinWalletService: BitcoinWalletService,
) : SpeedUpTransactionService {
  override suspend fun prepareTransactionSpeedUp(
    account: Account,
    transaction: BitcoinTransaction,
  ): Result<SpeedUpTransactionResult, SpeedUpTransactionError> {
    val txData = validateTransactionData(
      transaction = transaction,
      wallet = bitcoinWalletService.spendingWallet().value
    ).getOrElse { return Err(it) }

    // BIP125 Rule #1: only attempt replacements on transactions that explicitly opted-in by
    // setting at least one input sequence below 0xFFFFFFFE.
    // Note: BDK also enforces this, but we check upfront to provide a clear error message
    // before attempting expensive PSBT construction.
    if (!transaction.signalsOptInRbf()) {
      logWarn { "Transaction speed-up failed: transaction does not signal RBF" }
      return Err(SpeedUpTransactionError.TransactionNotReplaceable)
    }

    // BIP125 Rule #5: The number of original transactions and their descendants evicted from the
    // mempool must not exceed 100 transactions total. In a wallet context, users will not create
    // transaction chains of this size. We rely on BDK and the Bitcoin network to enforce this
    // limit if it's ever reached.

    val targetFeeRate = determineTargetFeeRate(
      account = account,
      originalFee = txData.fee,
      originalVsize = txData.vsize
    )

    val psbt =
      txData.wallet
        .createSignedPsbt(BumpFee(txid = transaction.id, feeRate = targetFeeRate))
        // BDK enforces BIP125 rule #2 by only allowing unconfirmed inputs already present in the
        // original transaction to be reused inside the bump-fee template. Rule #3 (absolute fee
        // must be >= originals) is also handled internally by BDK's fee bump logic.
        .logFailure { "Unable to build fee bump psbt" }
        .getOrElse {
          return Err(
            when (it) {
              is BdkError.InsufficientFunds -> SpeedUpTransactionError.InsufficientFunds
              is BdkError.FeeRateTooLow -> SpeedUpTransactionError.FeeRateTooLow
              else -> SpeedUpTransactionError.FailedToPrepareData
            }
          )
        }

    val details = SpeedUpTransactionDetails(
      txid = transaction.id,
      recipientAddress = txData.recipientAddress,
      sendAmount = transaction.subtotal,
      oldFee = Fee(amount = txData.fee),
      transactionType = transaction.transactionType
    )

    return Ok(SpeedUpTransactionResult(psbt, targetFeeRate, details))
  }

  private fun validateTransactionData(
    transaction: BitcoinTransaction,
    wallet: SpendingWallet?,
  ): Result<ValidatedTxData, SpeedUpTransactionError> {
    val fee = transaction.fee
      ?: run {
        logError { "Transaction speed-up failed: missing fee" }
        return Err(SpeedUpTransactionError.FailedToPrepareData)
      }

    val vsize = transaction.vsize
      ?: run {
        logError { "Transaction speed-up failed: missing vsize" }
        return Err(SpeedUpTransactionError.FailedToPrepareData)
      }

    val recipientAddress = transaction.recipientAddress
      ?: run {
        logError { "Transaction speed-up failed: missing recipient address" }
        return Err(SpeedUpTransactionError.FailedToPrepareData)
      }

    val feeRate = transaction.feeRate()
      ?: run {
        logError { "Transaction speed-up failed: missing fee rate" }
        return Err(SpeedUpTransactionError.FailedToPrepareData)
      }

    val spendingWallet = wallet
      ?: run {
        logError { "Transaction speed-up failed: no spending wallet available" }
        return Err(SpeedUpTransactionError.FailedToPrepareData)
      }

    return Ok(ValidatedTxData(fee, vsize, recipientAddress, feeRate, spendingWallet))
  }

  private data class ValidatedTxData(
    val fee: BitcoinMoney,
    val vsize: ULong,
    val recipientAddress: BitcoinAddress,
    val feeRate: FeeRate,
    val wallet: SpendingWallet,
  )

  private suspend fun determineTargetFeeRate(
    account: Account,
    originalFee: BitcoinMoney,
    originalVsize: ULong,
  ): FeeRate {
    val estimatedFeeRate =
      feeRateEstimator.estimatedFeeRateForTransaction(
        networkType = account.config.bitcoinNetworkType,
        estimatedTransactionPriority = FASTEST
      )

    // For test accounts on Signet, we manually choose a fee rate that is 5 times the previous
    // one. This is particularly useful for QA when testing.
    val baseFeeRate =
      if (account.config.isTestAccount && account.config.bitcoinNetworkType == SIGNET) {
        FeeRate(satsPerVByte = estimatedFeeRate.satsPerVByte * 5)
      } else {
        estimatedFeeRate
      }

    val minBip125FeeRate = calculateMinimumBip125FeeRate(originalFee, originalVsize)

    return if (baseFeeRate.satsPerVByte >= minBip125FeeRate.satsPerVByte) {
      baseFeeRate
    } else {
      minBip125FeeRate
    }
  }

  private fun calculateMinimumBip125FeeRate(
    originalFee: BitcoinMoney,
    originalVsize: ULong,
  ): FeeRate {
    // BIP 125 Rule #4: The replacement must pay at least originalFee + (1 sat/vB * tx size).
    // We use the original transaction's vsize as an approximation since the replacement will be similar.
    //
    // TODO W-8153: This calculation doesn't account for package weight when the original transaction
    // spends from unconfirmed outputs. We should include the weight of all unconfirmed parent
    // transactions in the package when calculating the minimum fee rate, as miners must confirm
    // the entire package together.
    val minRelayIncrement = BitcoinMoney.sats(originalVsize.toLong())
    val minRequiredFee = originalFee + minRelayIncrement
    return FeeRate(
      satsPerVByte = minRequiredFee.fractionalUnitValue.floatValue(exactRequired = false) / originalVsize.toFloat()
    )
  }

  private fun BitcoinTransaction.signalsOptInRbf(): Boolean =
    inputs.any { it.sequence < BIP125_SEQUENCE_SIGNAL_THRESHOLD }

  private companion object {
    // Transactions signal opt-in RBF when any input's nSequence is less than 0xFFFFFFFE.
    private val BIP125_SEQUENCE_SIGNAL_THRESHOLD = UInt.MAX_VALUE - 1u
  }
}
