package build.wallet.bitcoin.transactions

import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.BitcoinNetworkType.SIGNET
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.BitcoinFeeRateEstimator
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod.FeeBump
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod.FeeBumpWithDrain
import build.wallet.bitkey.account.Account
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.Bdk2FeatureFlag
import build.wallet.feature.isEnabled
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
  private val feeBumpAllowShrinkingChecker: FeeBumpAllowShrinkingChecker,
  private val bdk2FeatureFlag: Bdk2FeatureFlag,
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
      originalWeight = txData.weight
    )

    // When BDK2 is enabled, check if we need output shrinking (FeeBumpWithDrain) for sweeps
    // and single-UTXO consolidations. Only attempt when confirmed UTXO data is available;
    // otherwise fall back to FeeBump to avoid shrinking when additional confirmed UTXOs might exist.
    val walletUtxos = bitcoinWalletService.transactionsData().value?.utxos?.confirmed?.toList()
    val shrinkingScript = if (bdk2FeatureFlag.isEnabled() && walletUtxos != null) {
      feeBumpAllowShrinkingChecker.allowShrinkingOutputScript(
        transaction = transaction,
        walletUnspentOutputs = walletUtxos
      )
    } else {
      null // Legacy path or missing UTXO data - FeeBump handles internally
    }

    val constructionMethod = if (shrinkingScript != null) {
      FeeBumpWithDrain(
        txid = transaction.id,
        feeRate = targetFeeRate,
        drainToScript = shrinkingScript
      )
    } else {
      FeeBump(txid = transaction.id, feeRate = targetFeeRate)
    }

    val psbt = txData.wallet
      .createSignedPsbt(constructionMethod)
      // BDK enforces BIP125 rule #2 by only allowing unconfirmed inputs already present in the
      // original transaction to be reused inside the bump-fee template. Rule #3 (absolute fee
      // must be >= originals) is also handled internally by BDK's fee bump logic.
      .logFailure {
        val method = if (shrinkingScript != null) "FeeBumpWithDrain" else "FeeBump"
        "Unable to build fee bump PSBT using $method"
      }
      .getOrElse {
        return Err(
          when (it) {
            is BdkError.InsufficientFunds -> SpeedUpTransactionError.InsufficientFunds
            is BdkError.FeeRateTooLow -> SpeedUpTransactionError.FeeRateTooLow
            is BdkError.OutputBelowDustLimit -> SpeedUpTransactionError.OutputBelowDustLimit
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

    if (vsize == 0uL) {
      logError { "Transaction speed-up failed: invalid vsize=0" }
      return Err(SpeedUpTransactionError.FailedToPrepareData)
    }

    // Weight is preferred for precise fee rate calculation, but fall back to vsize * 4 if unavailable.
    // The max error from this fallback is 3 WU (< 1 vB) since vsize = ceil(weight/4),
    // which is <= 0.75 sats at 1 sat/vB.
    val weight = transaction.weight
      ?: run {
        val fallbackWeight = vsize * 4uL
        logWarn { "Fee bump: transaction weight missing; using fallback" }
        fallbackWeight
      }
    if (weight == 0uL) {
      logError { "Transaction speed-up failed: invalid weight=0" }
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

    return Ok(ValidatedTxData(fee, vsize, weight, recipientAddress, feeRate, spendingWallet))
  }

  private data class ValidatedTxData(
    val fee: BitcoinMoney,
    val vsize: ULong,
    val weight: ULong,
    val recipientAddress: BitcoinAddress,
    val feeRate: FeeRate,
    val wallet: SpendingWallet,
  )

  private suspend fun determineTargetFeeRate(
    account: Account,
    originalFee: BitcoinMoney,
    originalWeight: ULong,
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
    val minBip125FeeRate = calculateMinimumBip125FeeRate(originalFee, originalWeight)

    val targetFeeRate = if (baseFeeRate.satsPerVByte >= minBip125FeeRate.satsPerVByte) {
      baseFeeRate
    } else {
      minBip125FeeRate
    }
    return targetFeeRate
  }

  private fun calculateMinimumBip125FeeRate(
    originalFee: BitcoinMoney,
    originalWeight: ULong,
  ): FeeRate {
    // BIP 125 Rule #4: The replacement must pay at least originalFee + (incremental relay fee).
    // The incremental relay fee is 1 sat/vB, which equals 0.25 sat/WU or 250 sat/kWU.
    //
    // We calculate directly in sat/kWU using actual weight to match BDK's internal calculation,
    // then convert to sat/vB for our FeeRate type.
    //
    // TODO W-8153: This calculation doesn't account for package weight when the original transaction
    // spends from unconfirmed outputs. We should include the weight of all unconfirmed parent
    // transactions in the package when calculating the minimum fee rate, as miners must confirm
    // the entire package together.
    val originalFeeSats = originalFee.fractionalUnitValue.longValue()
    // Incremental relay fee: 1 sat/vB = 0.25 sat/WU. For weight WU, that's ceil(weight/4) sats.
    val incrementalRelaySats = (originalWeight + 3uL) / 4uL // ceil(weight/4)
    val minRequiredFeeSats = originalFeeSats + incrementalRelaySats.toLong()
    // Calculate in sat/kWU with ceiling to match BDK's internal calculation and avoid off-by-one.
    // sat/kWU = ceil(sats * 1000 / weight), then convert to sat/vB = sat/kWU / 250
    val minFeeRateSatPerKwu = (minRequiredFeeSats * 1000 + originalWeight.toLong() - 1) / originalWeight.toLong() // ceil division
    return FeeRate(satsPerVByte = minFeeRateSatPerKwu / 250f)
  }

  private fun BitcoinTransaction.signalsOptInRbf(): Boolean =
    inputs.any { it.sequence < BIP125_SEQUENCE_SIGNAL_THRESHOLD }
}
